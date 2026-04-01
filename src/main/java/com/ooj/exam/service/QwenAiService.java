package com.ooj.exam.service;


import com.ooj.exam.vo.AiGenerateRequestVo;

/**
 * Kimi AI服务接口
 * 用于调用Kimi API生成题目
 */
public interface QwenAiService {

    String buildPrompt(AiGenerateRequestVo request);

    /**
     * 调用Kimi API生成题目
     * @param prompt
     * @return 模型反馈结果
     */
    String callQwenAi(String prompt) throws InterruptedException;
}