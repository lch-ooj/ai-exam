package com.ooj.exam.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ooj.exam.entity.Question;
import com.ooj.exam.properties.QwenProperties;
import com.ooj.exam.service.QwenAiService;
import com.ooj.exam.vo.AiGenerateRequestVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * AI生成服务实现类
 */
@Slf4j
@Service
public class QwenAiServiceImpl implements QwenAiService {

    @Autowired
    private WebClient webClient;
    @Autowired
    private QwenProperties qwenProperties;

    /**
     * 构建发送给AI的提示词
     */
    public String buildPrompt(AiGenerateRequestVo request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("请为我生成").append(request.getCount()).append("道关于【")
                .append(request.getTopic()).append("】的题目。\n\n");

        prompt.append("要求：\n");

        // 题目类型要求
        if (request.getTypes() != null && !request.getTypes().isEmpty()) {
            List<String> typeList = Arrays.asList(request.getTypes().split(","));
            prompt.append("- 题目类型：");
            for (String type : typeList) {
                switch (type.trim()) {
                    case "CHOICE":
                        prompt.append("选择题");
                        if (request.getIncludeMultiple() != null && request.getIncludeMultiple()) {
                            prompt.append("(包含单选和多选)");
                        }
                        prompt.append(" ");
                        break;
                    case "JUDGE":
                        prompt.append("判断题（**重要：确保正确答案和错误答案的数量大致平衡，不要全部都是正确或错误**） ");
                        break;
                    case "TEXT":
                        prompt.append("简答题 ");
                        break;
                }
            }
            prompt.append("\n");
        }

        // 难度要求
        if (request.getDifficulty() != null) {
            String difficultyText = switch (request.getDifficulty()) {
                case "EASY" -> "简单";
                case "MEDIUM" -> "中等";
                case "HARD" -> "困难";
                default -> "中等";
            };
            prompt.append("- 难度等级：").append(difficultyText).append("\n");
        }

        // 额外要求
        if (request.getRequirements() != null && !request.getRequirements().isEmpty()) {
            prompt.append("- 特殊要求：").append(request.getRequirements()).append("\n");
        }

        // 判断题特别要求
        if (request.getTypes() != null && request.getTypes().contains("JUDGE")) {
            prompt.append("- **判断题特别要求**：\n");
            prompt.append("  * 确保生成的判断题中，正确答案(TRUE)和错误答案(FALSE)的数量尽量平衡\n");
            prompt.append("  * 不要所有判断题都是正确的或都是错误的\n");
            prompt.append("  * 错误的陈述应该是常见的误解或容易混淆的概念\n");
            prompt.append("  * 正确的陈述应该是重要的基础知识点\n");
        }

        prompt.append("\n请严格按照以下JSON格式返回，不要包含任何其他文字：\n");
        prompt.append("```json\n");
        prompt.append("{\n");
        prompt.append("  \"questions\": [\n");
        prompt.append("    {\n");
        prompt.append("      \"title\": \"题目内容\",\n");
        prompt.append("      \"type\": \"CHOICE|JUDGE|TEXT\",\n");
        prompt.append("      \"multi\": true/false,\n");
        prompt.append("      \"difficulty\": \"EASY|MEDIUM|HARD\",\n");
        prompt.append("      \"score\": 5,\n");
        prompt.append("      \"choices\": [\n");
        prompt.append("        {\"content\": \"选项内容\", \"isCorrect\": true/false, \"sort\": 1}\n");
        prompt.append("      ],\n");
        prompt.append("      \"answer\": \"TRUE或FALSE(判断题专用)|文本答案(简答题专用)\",\n");
        prompt.append("      \"analysis\": \"题目解析\"\n");
        prompt.append("    }\n");
        prompt.append("  ]\n");
        prompt.append("}\n");
        prompt.append("```\n\n");

        prompt.append("注意：\n");
        prompt.append("1. 选择题必须有choices数组，判断题和简答题设置answer字段\n");
        prompt.append("2. 多选题的multi字段设为true，单选题设为false\n");
        prompt.append("3. **判断题的answer字段只能是\"TRUE\"或\"FALSE\"，请确保答案分布合理**\n");
        prompt.append("4. 每道题都要有详细的解析\n");
        prompt.append("5. 题目要有实际价值，贴近实际应用场景\n");
        prompt.append("6. 严格按照JSON格式返回，确保可以正确解析\n");

        // 如果只生成判断题，额外强调答案平衡
        if (request.getTypes() != null && request.getTypes().equals("JUDGE") && request.getCount() > 1) {
            prompt.append("7. **判断题答案分布要求**：在").append(request.getCount()).append("道判断题中，");
            int halfCount = request.getCount() / 2;
            if (request.getCount() % 2 == 0) {
                prompt.append("请生成").append(halfCount).append("道正确(TRUE)和").append(halfCount).append("道错误(FALSE)的题目");
            } else {
                prompt.append("请生成约").append(halfCount).append("-").append(halfCount + 1).append("道正确(TRUE)和约").append(halfCount).append("-").append(halfCount + 1).append("道错误(FALSE)的题目");
            }
        }

        return prompt.toString();
    }

    /**
     * 调用 Qwen API 并且解析得到的结果content
     * @param prompt
     * @return
     * @throws InterruptedException
     */
    @Override
    public String callQwenAi(String prompt) throws InterruptedException {
        //1.构建重试代码
        /**
         * 创建一个重试策略，最多重试3次
         * 调用成功
         * 调用失败
         *  1.限速/api欠费
         *  2.请求数据结构的问题
         *  3.返回的结果格式不对
         *  4.网络问题
         * 解决方案：
         *  1.服务降级，部分成功
         *  2.失败重试
         */
        int maxTry = 3;
        for (int i = 1; i <= 3; i++) {
            try {
                //2.webclient调用qwen
                Map<String, Object> requestBody = new HashMap<>();
                requestBody.put("model", qwenProperties.getModel());

                List<Map<String, Object>> messages = new ArrayList<>();
                Map<String, Object> userMessage = new HashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", prompt);
                messages.add(userMessage);

                requestBody.put("messages", messages);

                String response = webClient.post()
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();

                //3.解析结果(成功/失败)
                //判断是否有error
                JSONObject resultObject = (JSONObject) JSONObject.parse(response);
                if (resultObject != null && resultObject.containsKey("error")) {
                    String errorMessage = resultObject.getJSONObject("error").getString("message");
                    throw new RuntimeException(errorMessage);
                }

                String content = resultObject.getJSONArray("choices").getJSONObject(0)
                            .getJSONObject("message").getString("content");

                if (content == null){
                    throw new RuntimeException("qwen接口返回结构正确，但内容为空");
                }
                return content;

            } catch (Exception e){
                Thread.sleep(1000);
                log.error("调用qwen接口失败，进行第{}次重试",i);
                if (i == maxTry ){
                    throw new RuntimeException("已经重试%s次，请稍后再试".formatted(maxTry));
                }
            }
        }
        throw new RuntimeException("已经重试%s次，请稍后再试".formatted(maxTry));
    }

    /**
     * 构建简答题评分提示词
     * @param question
     * @param userAnswer
     * @param maxScore
     * @return
     */
    @Override
    public String buildGradingPrompt(Question question, String userAnswer, Integer maxScore) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名专业的考试阅卷老师，请对以下题目进行判卷：\n\n");

        prompt.append("【题目信息】\n");
        prompt.append("题型：").append(getQuestionTypeText(question.getType())).append("\n");
        prompt.append("题目：").append(question.getTitle()).append("\n");
        prompt.append("标准答案：").append(question.getAnswer()).append("\n");
        prompt.append("满分：").append(maxScore).append("分\n\n");

        prompt.append("【学生答案】\n");
        prompt.append(userAnswer.trim().isEmpty() ? "（未作答）" : userAnswer).append("\n\n");

        prompt.append("【判卷要求】\n");
        if ("CHOICE".equals(question.getType()) || "JUDGE".equals(question.getType())) {
            prompt.append("- 客观题：答案完全正确得满分，答案错误得0分\n");
        } else if ("TEXT".equals(question.getType())) {
            prompt.append("- 主观题：根据答案的准确性、完整性、逻辑性进行评分\n");
            prompt.append("- 答案要点正确且完整：80-100%分数\n");
            prompt.append("- 答案基本正确但不够完整：60-80%分数\n");
            prompt.append("- 答案部分正确：30-60%分数\n");
            prompt.append("- 答案完全错误或未作答：0分\n");
        }

        prompt.append("\n请按以下JSON格式返回判卷结果：\n");
        prompt.append("{\n");
        prompt.append("  \"score\": 实际得分(整数),\n");
        prompt.append("  \"feedback\": \"具体的评价反馈(50字以内)\",\n");
        prompt.append("  \"reason\": \"扣分原因或得分依据(30字以内)\"\n");
        prompt.append("}");

        return prompt.toString();
    }

    /**
     * 获取题目类型文本
     */
    private String getQuestionTypeText(String type) {
        Map<String, String> typeMap = new HashMap<>();
        typeMap.put("CHOICE", "选择题");
        typeMap.put("JUDGE", "判断题");
        typeMap.put("TEXT", "简答题");
        return typeMap.getOrDefault(type, "未知题型");
    }

    /**
     * 构建考试总结的提示词
     * @param totalScore
     * @param maxScore
     * @param questionCount
     * @param correctCount
     * @return
     */
    @Override
    public String buildSummaryPrompt(Integer totalScore, Integer maxScore, Integer questionCount, Integer correctCount) {
        double percentage = (double) totalScore / maxScore * 100;

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一名资深的教育专家，请为学生的考试表现提供专业的总评和学习建议：\n\n");

        prompt.append("【考试成绩】\n");
        prompt.append("总得分：").append(totalScore).append("/").append(maxScore).append("分\n");
        prompt.append("得分率：").append(String.format("%.1f", percentage)).append("%\n");
        prompt.append("题目总数：").append(questionCount).append("道\n");
        prompt.append("答对题数：").append(correctCount).append("道\n\n");

        prompt.append("【要求】\n");
        prompt.append("请提供一份150字左右的考试总评，包括：\n");
        prompt.append("1. 对本次考试表现的客观评价\n");
        prompt.append("2. 指出优势和不足之处\n");
        prompt.append("3. 提供具体的学习建议和改进方向\n");
        prompt.append("4. 给予鼓励和激励\n\n");

        prompt.append("请直接返回总评内容，无需特殊格式：");

        return prompt.toString();
    }
}