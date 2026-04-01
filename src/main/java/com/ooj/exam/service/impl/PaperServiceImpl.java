package com.ooj.exam.service.impl;


import com.ooj.exam.entity.Paper;
import com.ooj.exam.entity.PaperQuestion;
import com.ooj.exam.entity.Question;
import com.ooj.exam.mapper.PaperMapper;
import com.ooj.exam.mapper.PaperQuestionMapper;
import com.ooj.exam.mapper.QuestionMapper;
import com.ooj.exam.service.PaperService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.vo.PaperVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


/**
 * 试卷服务实现类
 */
@Slf4j
@Service
public class PaperServiceImpl extends ServiceImpl<PaperMapper, Paper> implements PaperService {


    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Paper createPaper(PaperVo paperVo) {
        // 1. 创建试卷基本信息
        Paper paper = new Paper();
//        BeanUtils.copyProperties(paperVo, paper);
        paper.setName(paperVo.getName());
        paper.setDescription(paperVo.getDescription());
        paper.setDuration(paperVo.getDuration());
        paper.setStatus("DRAFT"); // 默认草稿状态

        // 2. 计算总分和题目数量
        Map<Integer, BigDecimal> questions = paperVo.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("试卷必须至少包含一道题目");
        }

        BigDecimal totalScore = questions.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paper.setTotalScore(totalScore);
        paper.setQuestionCount(questions.size());

        // 3. 保存试卷。得到试卷ID，用于保存试卷-题目关联关系
        this.save(paper);

        //4.将试卷id和传入的题目信息转换成试卷-题目中间表对象集合
        List<PaperQuestion> paperQuestions = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : questions.entrySet()) {
            PaperQuestion pq = new PaperQuestion(paper.getId(), entry.getKey().longValue(), entry.getValue());
            paperQuestions.add(pq);
        }
        // 5. 保存试卷-题目关联关系
        paperQuestionMapper.insertBatch(paperQuestions);

        log.info("创建试卷成功，试卷 ID: {}, 名称：{}", paper.getId(), paper.getName());
        return paper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Paper updatePaper(Integer id, PaperVo paperVo) {
        // 1. 查询试卷是否存在
        Paper paper = this.getById(id);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }

        // 2. 已发布的试卷不能修改
        if ("PUBLISHED".equals(paper.getStatus())) {
            throw new IllegalStateException("已发布的试卷不能修改");
        }

        // 3. 更新试卷基本信息
        paper.setName(paperVo.getName());
        paper.setDescription(paperVo.getDescription());
        paper.setDuration(paperVo.getDuration());

        // 4. 更新题目配置
        Map<Integer, BigDecimal> questions = paperVo.getQuestions();
        if (questions == null || questions.isEmpty()) {
            throw new IllegalArgumentException("试卷必须至少包含一道题目");
        }

        // 5. 计算总分和题目数量
        BigDecimal totalScore = questions.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        paper.setTotalScore(totalScore);
        paper.setQuestionCount(questions.size());

        // 6. 删除原有的试卷-题目关联
        paperQuestionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaperQuestion>()
                .eq("paper_id", id));

        // 7. 插入新的试卷-题目关联
        List<PaperQuestion> paperQuestions = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : questions.entrySet()) {
            PaperQuestion pq = new PaperQuestion(id.longValue(), entry.getKey().longValue(), entry.getValue());
            paperQuestions.add(pq);
        }
        paperQuestionMapper.insertBatch(paperQuestions);

        // 8. 更新试卷
        this.updateById(paper);

        log.info("更新试卷成功，试卷 ID: {}, 名称：{}", id, paper.getName());
        return paper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePaperStatus(Integer id, String status) {
        // 1. 查询试卷是否存在
        Paper paper = this.getById(id);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }

        // 2. 验证状态值
        if (!"PUBLISHED".equals(status) && !"DRAFT".equals(status)) {
            throw new IllegalArgumentException("无效的状态值，只能是 PUBLISHED 或 DRAFT");
        }

        // 3. 更新状态
        paper.setStatus(status);
        this.updateById(paper);

        log.info("更新试卷状态成功，试卷 ID: {}, 新状态：{}", id, status);
    }

    @Override
    public Paper getPaperDetail(Integer id) {
        // 1. 查询试卷基本信息
        Paper paper = this.getById(id);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }

        // 2. 查询试卷-题目关联
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaperQuestion>()
                        .eq("paper_id", id)
        );

        if (paperQuestions == null || paperQuestions.isEmpty()) {
            paper.setQuestions(new ArrayList<>());
            return paper;
        }

        // 3. 查询题目详细信息
        List<Long> questionIds = paperQuestions.stream()
                .map(PaperQuestion::getQuestionId)
                .collect(Collectors.toList());

        List<Question> questions = questionMapper.selectBatchIds(questionIds);

        // 4. 为每个题目设置其在试卷中的分值
        Map<Long, BigDecimal> scoreMap = paperQuestions.stream()
                .collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));

        for (Question question : questions) {
            BigDecimal score = scoreMap.get(question.getId());
            question.setPaperScore(score);
        }

        paper.setQuestions(questions);
        return paper;
    }
} 