package com.ooj.exam.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ooj.exam.entity.AnswerRecord;
import com.ooj.exam.entity.ExamRecord;
import com.ooj.exam.entity.Paper;
import com.ooj.exam.mapper.AnswerRecordMapper;
import com.ooj.exam.mapper.ExamRecordMapper;
import com.ooj.exam.service.ExamService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.service.PaperService;
import com.ooj.exam.vo.StartExamVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;


/**
 * 考试服务实现类
 */
@Service
@Slf4j
public class ExamServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamService {
    @Autowired
    private PaperService paperService;

    @Autowired
    private AnswerRecordMapper answerRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamRecord startExam(StartExamVo startExamVo) {
        // 1. 验证试卷是否存在
        Integer paperId = startExamVo.getPaperId();
        Paper paper = paperService.getById(paperId);
        if (paper == null) {
            throw new IllegalArgumentException("试卷不存在");
        }

        // 2. 检查学生当前考试的试卷是否正在考试中
        QueryWrapper<ExamRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_name", startExamVo.getStudentName())
                .eq("exam_id", paperId)
                .eq("status", "进行中");

        ExamRecord examRecord = this.getOne(queryWrapper);
        if (examRecord != null) {
            // 如果正在考试中，回显正在考试的记录
            log.info("学生正在考试中，返回当前考试记录，考试记录 ID: {}, 试卷 ID: {}, 考生：{}",
                    examRecord.getId(), paperId, startExamVo.getStudentName());

            // 获取试卷详情并设置到考试记录中
            Paper ongoingPaper = paperService.getPaperById(examRecord.getExamId());
            examRecord.setPaper(ongoingPaper);

            return examRecord;
        }

        // 3. 创建新的考试记录
        examRecord = new ExamRecord();
        examRecord.setExamId(paperId);
        examRecord.setStudentName(startExamVo.getStudentName());
        examRecord.setStartTime(LocalDateTime.now());
        examRecord.setStatus("进行中");
        examRecord.setWindowSwitches(0);

        // 4. 保存考试记录
        this.save(examRecord);

        log.info("考试开始成功，考试记录 ID: {}, 考生：{}, 试卷：{}",
                examRecord.getId(), startExamVo.getStudentName(), paper.getName());

        return examRecord;
    }

    @Override
    public ExamRecord getExamRecordById(Integer id) {
        // 1. 查询考试记录基本信息
        ExamRecord examRecord = this.getById(id);
        if (examRecord == null) {
            throw new IllegalArgumentException("考试记录不存在");
        }

        // 2. 获取试卷详情（包含题目信息）
        Paper paper = paperService.getPaperById(examRecord.getExamId());
        examRecord.setPaper(paper);

        // 3. 查询答题记录详情
        QueryWrapper<AnswerRecord> answerWrapper = new QueryWrapper<>();
        answerWrapper.eq("exam_record_id", id);
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(answerWrapper);

        // 4. 设置答题记录列表
        examRecord.setAnswerRecords(answerRecords);
        System.out.println(examRecord);

        log.info("考试记录详情查询成功，考试记录 ID: {}, 考生：{}, 状态：{}, 得分：{}",
                id, examRecord.getStudentName(), examRecord.getStatus(), examRecord.getScore());

        return examRecord;
    }
} 