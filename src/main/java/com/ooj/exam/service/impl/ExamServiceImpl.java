package com.ooj.exam.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ooj.exam.entity.*;
import com.ooj.exam.mapper.AnswerRecordMapper;
import com.ooj.exam.mapper.ExamRecordMapper;
import com.ooj.exam.mapper.QuestionAnswerMapper;
import com.ooj.exam.mapper.QuestionMapper;
import com.ooj.exam.service.*;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.vo.StartExamVo;
import com.ooj.exam.vo.SubmitAnswerVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


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
    @Autowired
    private AnswerRecordService answerRecordService;
    @Autowired
    private QuestionMapper questionMapper;
    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;
    @Autowired
    private QwenAiService qwenAiService;

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

    /**
     * 获取考试记录详情
     * @param id 考试记录 ID
     * @return
     */
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

        // 4. 如果答题记录不为空，按题目类型排序
        if (answerRecords != null && !answerRecords.isEmpty()) {
            // 构建题目 ID 到题目类型的映射
            Map<Integer, String> questionTypeMap = new HashMap<>();
            for (AnswerRecord answerRecord : answerRecords) {
                Question question = questionMapper.selectById(answerRecord.getQuestionId());
                if (question != null) {
                    questionTypeMap.put(question.getId().intValue(), question.getType());
                }
            }

            // 按题目类型排序：选择题 -> 判断题 -> 简答题
            answerRecords.sort((a1, a2) -> {
                String type1 = questionTypeMap.get(a1.getQuestionId());
                String type2 = questionTypeMap.get(a2.getQuestionId());

                if (type1 == null) type1 = "";
                if (type2 == null) type2 = "";

                return Integer.compare(getTypeOrder(type1), getTypeOrder(type2));
            });
        }

        // 5. 设置答题记录列表
        examRecord.setAnswerRecords(answerRecords);

        log.info("考试记录详情查询成功，考试记录 ID: {}, 考生：{}, 状态：{}, 得分：{}",
                id, examRecord.getStudentName(), examRecord.getStatus(), examRecord.getScore());

        return examRecord;
    }

    /**
     * 获取题目类型排序值 - 选择题 -> 判断题 -> 简答题
     * @param type 题目类型
     * @return 排序值
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void submitExam(Integer examRecordId, List<SubmitAnswerVo> answers) throws InterruptedException {
        //转换答案类型
        List<AnswerRecord> answerRecords = answers.stream()
                .map(answerVo -> new AnswerRecord(
                        examRecordId,
                        answerVo.getQuestionId(),
                        answerVo.getUserAnswer()
                ))
                .collect(Collectors.toList());
        //保存答案
        answerRecordService.saveBatch(answerRecords);
        //更新考试记录
        ExamRecord examRecord = this.getById(examRecordId);
        examRecord.setStatus("已完成");
        examRecord.setEndTime(LocalDateTime.now());
        this.updateById(examRecord);

        log.info("答案提交成功，考试记录 ID: {}, 考生：{}, 提交答案数量：{}, 考试结束时间：{}",
                examRecordId, examRecord.getStudentName(), answers.size(), examRecord.getEndTime());

        //调用业务方法完成判卷
        gradeExamByAI(examRecordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ExamRecord gradeExamByAI(Integer examRecordId) throws InterruptedException {
        // 1. 查询考试记录
        ExamRecord examRecord = this.getById(examRecordId);
        if (examRecord == null) {
            throw new IllegalArgumentException("考试记录不存在");
        }

        // 3. 查询答题记录
        QueryWrapper<AnswerRecord> answerWrapper = new QueryWrapper<>();
        answerWrapper.eq("exam_record_id", examRecordId);
        List<AnswerRecord> answerRecords = answerRecordMapper.selectList(answerWrapper);

        // 4. 批阅答题记录
        int totalScore = 0;
        int maxScore = 0;
        int questionCount = answerRecords.size();
        int correctCount = 0;
        for (AnswerRecord answerRecord : answerRecords) {
            // 查询题目信息
            Question question = questionMapper.selectById(answerRecord.getQuestionId());
            if (question == null) {
                log.warn("题目不存在，题目 ID: {}", answerRecord.getQuestionId());
                continue;
            }

            maxScore = maxScore + question.getScore();

            // 查询正确答案
            QueryWrapper<QuestionAnswer> answerQuery = new QueryWrapper<>();
            answerQuery.eq("question_id", question.getId());
            QuestionAnswer questionAnswer = questionAnswerMapper.selectOne(answerQuery);

            if (questionAnswer == null) {
                log.warn("题目答案不存在，题目 ID: {}", question.getId());
                continue;
            }

            // 根据题型进行判分 - 只分为简答题和非简答题
            String questionType = question.getType();
            String correctAnswer = questionAnswer.getAnswer();
            String userAnswer = answerRecord.getUserAnswer();

            if ("TEXT".equals(questionType)) {
                //  AI 批阅简答题
                String prompt = qwenAiService.buildGradingPrompt(question, userAnswer, question.getScore());
                String result = qwenAiService.callQwenAi(prompt);
                // 解析 AI 返回的 JSON 数据
                try {
                    JSONObject gradingResult = JSONObject.parseObject(result);

                    // 提取评分结果
                    Integer score = gradingResult.getInteger("score");
                    String feedback = gradingResult.getString("feedback");
                    String reason = gradingResult.getString("reason");

                    // 解析结果
                    if (score >= question.getScore().intValue()){
                        //完全正确
                        answerRecord.setScore(question.getScore().intValue());
                        answerRecord.setIsCorrect(1);
                        answerRecord.setAiCorrection(feedback);
                        correctCount = correctCount + 1;
                    } else if (score <= 0){
                        //完全错误
                        answerRecord.setScore(0);
                        answerRecord.setIsCorrect(0);
                        answerRecord.setAiCorrection(reason);
                    } else {
                        //部分正确
                        answerRecord.setScore(score);
                        answerRecord.setIsCorrect(2);
                        answerRecord.setAiCorrection(reason);
                    }

                    totalScore += score;

                    log.info("简答题 AI 批阅完成，题目 ID: {}, 满分：{}, 得分：{}, 反馈：{}",
                            question.getId(), maxScore, score, feedback);

                } catch (Exception e) {
                    log.error("解析 AI 评分结果失败，题目 ID: {}, 错误信息：{}", question.getId(), e.getMessage());
                    // AI 批阅失败，暂时给 0 分
                    answerRecord.setScore(0);
                    answerRecord.setIsCorrect(null);
                    answerRecord.setAiCorrection("AI 批阅失败：" + e.getMessage());
                }
            } else {
                // 非简答题（选择题和判断题）判分
                // 标准化答案：判断题需要将 T/F 转换为 TRUE/FALSE
                String normalizedUserAnswer = normalizeJudgeAnswer(userAnswer);
                String normalizedCorrectAnswer = normalizeJudgeAnswer(correctAnswer);

                if (correctAnswer != null && normalizedCorrectAnswer.equals(normalizedUserAnswer)) {
                    answerRecord.setScore(question.getScore());
                    answerRecord.setIsCorrect(1);
                    totalScore += question.getScore();
                    correctCount = correctCount + 1;
                } else {
                    answerRecord.setScore(0);
                    answerRecord.setIsCorrect(0);
                }
            }

            // 更新答题记录
            answerRecordMapper.updateById(answerRecord);
        }

        //ai评语
        String prompt = qwenAiService.buildSummaryPrompt(totalScore, maxScore, questionCount, correctCount);
        String summary = qwenAiService.callQwenAi(prompt);

        // 5. 更新考试记录
        examRecord.setScore(totalScore);
        examRecord.setStatus("已批阅");
        examRecord.setAnswers(summary);
        this.updateById(examRecord);

        log.info("AI 批阅完成，考试记录 ID: {}, 考生：{}, 总分：{}, 批阅题目数：{}",
                examRecordId, examRecord.getStudentName(), totalScore, answerRecords.size());

        // 6. 返回批阅后的考试记录
        return getExamRecordById(examRecordId);
    }

    /**
     * 标准化判断题答案 - 将 T/F 转换为 TRUE/FALSE
     * 对于非判断题的答案，保持原样返回
     * @param userAnswer 用户答案
     * @return 标准化后的答案
     */
    private String normalizeJudgeAnswer(String userAnswer) {
        //trim()删除字符串两端的空格
        if (userAnswer == null || userAnswer.trim().isEmpty()) {
            return "";
        }

        String trimmed = userAnswer.trim().toUpperCase();
        if ("T".equals(trimmed) || "TRUE".equals(trimmed)) {
            return "TRUE";
        } else if ("F".equals(trimmed) || "FALSE".equals(trimmed)) {
            return "FALSE";
        }

        return userAnswer;
    }
} 