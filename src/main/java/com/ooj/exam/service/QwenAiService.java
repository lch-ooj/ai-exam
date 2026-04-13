package com.ooj.exam.service;


import com.ooj.exam.entity.Question;
import com.ooj.exam.vo.AiGenerateRequestVo;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface QwenAiService {

    /**
     * 构建生成题目的提示词
     * @param request
     * @return
     */
    String buildPrompt(AiGenerateRequestVo request);

    /**
     * 调用 Qwen API 并且解析得到的结果content
     * @param prompt
     * @return 模型反馈结果
     */
    String callQwenAi(String prompt) throws InterruptedException;

    /**
     * 构建简答题评分提示词
     * @param question
     * @param userAnswer
     * @param maxScore
     * @return
     */
    String buildGradingPrompt(Question question, String userAnswer, Integer maxScore);

    /**
     * 构建考试总结的提示词
     * @param totalScore
     * @param maxScore
     * @param questionCount
     * @param correctCount
     * @return
     */
    String buildSummaryPrompt(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount);

}