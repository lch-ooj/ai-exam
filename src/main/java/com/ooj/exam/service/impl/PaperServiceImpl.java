package com.ooj.exam.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ooj.exam.entity.ExamRecord;
import com.ooj.exam.entity.Paper;
import com.ooj.exam.entity.PaperQuestion;
import com.ooj.exam.entity.Question;
import com.ooj.exam.mapper.ExamRecordMapper;
import com.ooj.exam.mapper.PaperMapper;
import com.ooj.exam.mapper.PaperQuestionMapper;
import com.ooj.exam.mapper.QuestionMapper;
import com.ooj.exam.service.PaperQuestionService;
import com.ooj.exam.service.PaperService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.vo.AiPaperVo;
import com.ooj.exam.vo.PaperVo;
import com.ooj.exam.vo.RuleVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
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

    @Autowired
    private PaperQuestionService paperQuestionService;

    @Autowired
    private ExamRecordMapper examRecordMapper;

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
        paperQuestionService.saveBatch(paperQuestions);

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
        //试卷不能重名
        QueryWrapper<Paper> queryWrapper = new QueryWrapper<>();
        queryWrapper.ne("id", id);
        queryWrapper.eq("name", paperVo.getName());
        if (this.count(queryWrapper) > 0) {
            throw new IllegalArgumentException("试卷名称不能重复");
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
        paperQuestionService.saveBatch(paperQuestions);

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
    public Paper getPaperById(Integer id) {
        /**
         * SELECT * FROM paper_question pq
         *               JOIN questions qs on pq.question_id = qs.id
         *               JOIN question_answers qas on qas.question_id = qs.id
         *               LEFT JOIN question_choices qcs on qcs.question_id = qs.id and qcs.is_deleted = 0
         *               WHERE pq.paper_id = 21 and pq.is_deleted = 0
         *               and qs.is_deleted = 0 and qas.is_deleted = 0
         *               ORDER BY qcs.sort asc;
         *
         * #试卷题目中间表 join 题目表 join 答案表left join 选项表
         * #逻辑删除、选项排序、列明重复
         * ON 是关联表时过滤数据，WHERE 是关联后过滤整体结果
         */
        // 1. 查询试卷基本信息
        Paper paper = this.getById(id);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }
        //2.questionMapper定义一个多表查询， 根据试卷id查询对应的题目集合

        // 2. 查询试卷 - 题目关联
        List<PaperQuestion> paperQuestions = paperQuestionMapper.selectList(
                new QueryWrapper<PaperQuestion>().eq("paper_id", id)
        );

        if (paperQuestions == null || paperQuestions.isEmpty()) {
            paper.setQuestions(new ArrayList<>());
            return paper;
        }

        // 3. 构建题目 ID 到分值的映射
        Map<Long, BigDecimal> scoreMap = paperQuestions.stream()
                .collect(Collectors.toMap(PaperQuestion::getQuestionId, PaperQuestion::getScore));

        // 4. 获取所有题目 ID
        List<Long> questionIds = paperQuestions.stream()
                .map(PaperQuestion::getQuestionId)
                .collect(Collectors.toList());

        // 5. 查询题目详细信息（selectBatchIds 不保证顺序，需要手动排序）
        List<Question> questions = questionMapper.selectBatchIds(questionIds);

        // 6. 为每个题目设置其在试卷中的分值
        for (Question question : questions) {
            BigDecimal score = scoreMap.get(question.getId());
            question.setPaperScore(score);
        }

        // 7. 按题目类型排序：选择题 -> 判断题 -> 简答题
        questions.sort((q1, q2) -> {
            int order1 = getTypeOrder(q1.getType());
            int order2 = getTypeOrder(q2.getType());
            return Integer.compare(order1, order2);
        });

        // 8. 设置排序后的题目列表
        paper.setQuestions(questions);

        log.debug("试卷详情查询成功，试卷 ID: {}, 题目数量：{}, 排序后题目类型顺序：{}",
                id, questions.size(),
                questions.stream().map(Question::getType).collect(Collectors.toList()));

        return paper;
    }

    /**
     * 获取题目类型的排序值
     * @param type 题目类型
     * @return 排序值，越小越靠前
     */
    private int getTypeOrder(String type) {
        if (type == null) {
            return Integer.MAX_VALUE;
        }
        return switch (type) {
            case "CHOICE" -> 1;
            case "JUDGE" -> 2;
            case "TEXT" -> 3;
            default -> Integer.MAX_VALUE;
        };
    }

    /**
     * ai组卷
     * @param aiPaperVo
     * @return
     */
    @Override
    public Paper createPaperWithAI(AiPaperVo aiPaperVo) {
        //1.完成试卷基础信息的保存，获取试卷ID
        Paper paper = new Paper();
        paper.setName(aiPaperVo.getName());
        paper.setDescription(aiPaperVo.getDescription());
        paper.setDuration(aiPaperVo.getDuration());
        paper.setStatus("DRAFT");
        this.save(paper);
        log.info("创建试卷成功，试卷 ID: {}, 试卷名称：{}", paper.getId(), paper.getName());
        Long paperId = paper.getId();

        // 2. 循环每个规则，在每个规则下获取一定数量的题目
        List<PaperQuestion> paperQuestions = new ArrayList<>();
        //总分数
        BigDecimal totalScore = BigDecimal.ZERO;
        //题目总数
        int totalQuestionCount = 0;

        for (RuleVo rule : aiPaperVo.getRules()) {

            if (rule.getCount() == 0){
                continue;
            }

            // 构建查询条件（type = type and category_id in categoryIds）
            QueryWrapper<Question> wrapper = new QueryWrapper<>();

            // 设置题目类型条件
            wrapper.eq("type", rule.getType().name());

            // 设置分类条件（如果指定了分类）
            if (rule.getCategoryIds() != null && !rule.getCategoryIds().isEmpty()) {
                wrapper.in("category_id", rule.getCategoryIds());
            }

            // 查询符合条件的所有题目
            List<Question> allQuestions = questionMapper.selectList(wrapper);

            // 如果符合规则的题目数量为 0，跳过该规则
            if (allQuestions == null || allQuestions.isEmpty()) {
                log.warn("规则 [类型：{}, 分类：{}] 未找到任何题目，跳过",
                        rule.getType(), rule.getCategoryIds());
                continue;
            }

            // 计算实际需要抽取的题目数量（不超过实际可用数量）
            int actualCount = Math.min(rule.getCount(), allQuestions.size());

            // 随机打乱题目顺序并抽取指定数量
            Collections.shuffle(allQuestions);
            List<Question> selectedQuestions = allQuestions.subList(0, actualCount);
            totalQuestionCount += actualCount;

            // 将抽取的题目转换成试卷 - 题目关联对象
            BigDecimal scorePerQuestion = BigDecimal.valueOf(rule.getScore());
            for (Question question : selectedQuestions) {
                PaperQuestion pq = new PaperQuestion(paperId, question.getId(), scorePerQuestion);
                paperQuestions.add(pq);
                totalScore = totalScore.add(scorePerQuestion);
            }

            log.info("规则 [类型：{}, 分类：{}] 抽取题目 {} 道，实际抽取 {} 道",
                    rule.getType(), rule.getCategoryIds(), rule.getCount(), actualCount);
        }

        // 3. 验证是否至少有一道题目
        if (paperQuestions.isEmpty()) {
            throw new IllegalArgumentException("未能抽取到任何题目，请调整组卷规则");
        }

        // 4. 保存试卷 - 题目关联关系
        paperQuestionService.saveBatch(paperQuestions);

        // 5. 更新试卷的总分和题目数量
        paper.setTotalScore(totalScore);
        paper.setQuestionCount(totalQuestionCount);
        this.updateById(paper);

        log.info("AI 组卷完成，试卷 ID: {}, 名称：{}, 题目总数：{}, 总分：{}",
                paper.getId(), paper.getName(), totalQuestionCount, totalScore);

        // 6. 返回完整的试卷结构（包含题目信息）
        return getPaperById(paperId.intValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePaper(Integer id) {
        // 1. 查询试卷是否存在
        Paper paper = this.getById(id);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }

        // 2. 检查是否有正在进行的考试记录（考试记录表中的exam_id就是试卷id）
        QueryWrapper<ExamRecord> recordQueryWrapper = new QueryWrapper<>();
        recordQueryWrapper.eq("exam_id", id)
                .eq("status", "进行中");

        Long ongoingExamCount = examRecordMapper.selectCount(recordQueryWrapper);
        if (ongoingExamCount > 0) {
            throw new IllegalStateException("有考生正在考试，无法删除试卷");
        }

        // 3. 检查试卷状态，已发布的试卷不能删除
        if ("PUBLISHED".equals(paper.getStatus())) {
            throw new IllegalStateException("已发布的试卷不能删除");
        }

        // 4. 删除试卷 - 题目关联关系
        QueryWrapper<PaperQuestion> pqQueryWrapper = new QueryWrapper<>();
        pqQueryWrapper.eq("paper_id", id);
        paperQuestionMapper.delete(pqQueryWrapper);

        // 5. 删除试卷
        this.removeById(id.longValue());

        log.info("删除试卷成功，试卷 ID: {}, 名称：{}", id, paper.getName());
    }

}