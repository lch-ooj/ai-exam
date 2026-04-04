package com.ooj.exam.service;

import com.ooj.exam.entity.ExamRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ooj.exam.vo.StartExamVo;
import com.ooj.exam.vo.SubmitAnswerVo;

import java.util.List;

/**
 * 考试服务接口
 */
public interface ExamService extends IService<ExamRecord> {
    /**
     * 开始考试 - 创建考试记录并返回试卷内容
     * @param startExamVo 开始考试请求参数
     * @return 考试记录（包含试卷信息）
     */
    ExamRecord startExam(StartExamVo startExamVo);

    /**
     * 根据 ID 获取考试记录详情
     * @param id 考试记录 ID
     * @return 考试记录详情（包含试卷和答题记录）
     */
    ExamRecord getExamRecordById(Integer id);


    /**
     * 提交考试答案 - 保存答题记录并更新考试状态
     * @param examRecordId 考试记录 ID
     * @param answers 答案列表
     */
    void submitExam(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException;


    /**
     * AI 自动批阅试卷 - 批改选择题和判断题，计算总分
     * @param examRecordId 考试记录 ID
     * @return 批阅后的考试记录
     */
    ExamRecord gradeExamByAI(Integer examRecordId) throws InterruptedException;
}
 