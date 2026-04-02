package com.ooj.exam.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ooj.exam.common.CacheConstants;
import com.ooj.exam.entity.*;
import com.ooj.exam.mapper.PaperQuestionMapper;
import com.ooj.exam.mapper.QuestionAnswerMapper;
import com.ooj.exam.mapper.QuestionChoiceMapper;
import com.ooj.exam.mapper.QuestionMapper;
import com.ooj.exam.service.QuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.service.QwenAiService;
import com.ooj.exam.utils.ExcelUtil;
import com.ooj.exam.utils.RedisUtils;
import com.ooj.exam.vo.AiGenerateRequestVo;
import com.ooj.exam.vo.QuestionImportVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目 Service 实现类
 * 实现题目相关的业务逻辑
 */
@Slf4j
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionMapper, Question> implements QuestionService {

    @Autowired
    private QuestionChoiceMapper questionChoiceMapper;

    @Autowired
    private QuestionAnswerMapper questionAnswerMapper;

    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private PaperQuestionMapper paperQuestionMapper;

    @Autowired
    private QwenAiService qwenAiService;


    @Override
    public Page<Question> getQuestionList(Integer page, Integer size, Long categoryId, String difficulty, String type, String keyword) {
        // 创建分页对象
        Page<Question> questionPage = new Page<>(page, size);

        // 构建查询条件
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();

        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Question::getCategoryId, categoryId);
        }

        // 难度筛选
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }

        // 题型筛选
        if (StringUtils.hasText(type)) {
            wrapper.eq(Question::getType, type);
        }

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Question::getTitle, keyword);
        }

        // 按创建时间倒序
        wrapper.orderByDesc(Question::getCreateTime);

        // 执行分页查询
        Page<Question> result = this.page(questionPage, wrapper);

        // 填充每个题目的选项和答案信息
        fillQuestionDetails(result.getRecords());

        return result;
    }

    /**
     * 私有方法：批量填充题目的选项和答案信息
     *
     * 优化思路：
     * 1. 先收集所有题目 ID
     * 2. 使用 IN 查询一次性获取所有选项和答案
     * 3. 通过内存分组组装到对应的题目上
     *
     * 性能优势：
     * - 避免 N+1 查询问题（N 个题目需要 N+2 次查询）
     * - 只需 3 次查询：1 次题目 + 1 次选项 + 1 次答案
     *
     * @param questions 题目列表
     */
    private void fillQuestionDetails(List<Question> questions) {
        if (questions == null || questions.isEmpty()) {
            return;
        }

        // 1. 收集所有题目 ID
        List<Long> questionIds = questions.stream()
                .map(Question::getId)
                .collect(Collectors.toList());

        // 2. 批量查询所有选项（按题目 ID 分组）
        LambdaQueryWrapper<QuestionChoice> choiceWrapper = new LambdaQueryWrapper<>();
        choiceWrapper.in(QuestionChoice::getQuestionId, questionIds)
                .orderByAsc(QuestionChoice::getSort);
        List<QuestionChoice> allChoices = questionChoiceMapper.selectList(choiceWrapper);

        // 按题目 ID 分组：Map<questionId, List<choices>>
        Map<Long, List<QuestionChoice>> choiceMap = allChoices.stream()
                .collect(Collectors.groupingBy(QuestionChoice::getQuestionId));

        // 3. 批量查询所有答案（按题目 ID 分组）
        LambdaQueryWrapper<QuestionAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.in(QuestionAnswer::getQuestionId, questionIds);
        List<QuestionAnswer> allAnswers = questionAnswerMapper.selectList(answerWrapper);

        // 按题目 ID 分组：Map<questionId, QuestionAnswer>
        Map<Long, QuestionAnswer> answerMap = allAnswers.stream()
                .collect(Collectors.toMap(
                        QuestionAnswer::getQuestionId,
                        answer -> answer,
                        (v1, v2) -> v1 // 如果有重复，取第一个
                ));

        // 4. 将选项和答案组装到每个题目
        for (Question question : questions) {
            Long questionId = question.getId();

            // 设置选项列表
            List<QuestionChoice> choices = choiceMap.get(questionId);
            question.setChoices(choices != null ? choices : new ArrayList<>());

            // 设置答案
            QuestionAnswer answer = answerMap.get(questionId);
            question.setAnswer(answer);
        }
    }

    /**
     * 获取题目详情
     * @param id 题目 ID
     * @return
     */
    @Override
    public Question getQuestionWithDetails(Long id) {
        // 查询题目基本信息
        Question question = getById(id);
        if (question == null) {
            return null;
        }

        // 查询选项列表
        LambdaQueryWrapper<QuestionChoice> choiceWrapper = new LambdaQueryWrapper<>();
        choiceWrapper.eq(QuestionChoice::getQuestionId, id)
                .orderByAsc(QuestionChoice::getSort);
        List<QuestionChoice> choices = questionChoiceMapper.selectList(choiceWrapper);
        question.setChoices(choices);

        // 查询答案
        LambdaQueryWrapper<QuestionAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(QuestionAnswer::getQuestionId, id);
        QuestionAnswer answer = questionAnswerMapper.selectOne(answerWrapper);
        question.setAnswer(answer);

        //异步执行题目热度+1
        new Thread(() -> incrementQuestionScore(id)).start();

        return question;
    }

    /**
     * 用于题目热度访问加分
     * @param questionId
     */
    private void incrementQuestionScore(Long questionId) {
        redisUtils.zIncrementScore(CacheConstants.POPULAR_QUESTIONS_KEY, questionId, 1.0);
    }

    /**
     * 创建题目
     * @param question 题目信息
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Question createQuestionWithDetails(Question question) {
        // 1. 检查同一类型下题目名称是否重复
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getType, question.getType())
                .eq(Question::getTitle, question.getTitle());
        Long count = this.count(wrapper);

        if (count > 0) {
            throw new RuntimeException("该类型下已存在相同名称的题目：" + question.getTitle());
        }

        // 2. 保存题目信息
        boolean saved = this.save(question);
        if (!saved) {
            throw new RuntimeException("题目保存失败");
        }

        Long questionId = question.getId();
        System.out.println(question);

        // 3. 处理选择题：根据选项的 isCorrect 字段构建答案
        if ("CHOICE".equals(question.getType())) {
            if (question.getChoices() != null && !question.getChoices().isEmpty()) {
                // 保存所有选项
                for (int i = 0; i < question.getChoices().size(); i++) {
                    QuestionChoice choice = question.getChoices().get(i);
                    choice.setQuestionId(questionId);
                    choice.setSort(i);
                    questionChoiceMapper.insert(choice);
                }

                // 根据 isCorrect 字段提取正确答案标识（A,B,C,D）
                StringBuilder correctAnswer = new StringBuilder();
                for (int i = 0; i < question.getChoices().size(); i++) {
                    QuestionChoice choice = question.getChoices().get(i);
                    if (choice.getIsCorrect() != null && choice.getIsCorrect()) {
                        // 将索引转换为选项标识：0->A, 1->B, 2->C, 3->D
                        char optionLabel = (char) ('A' + i);
                        if (correctAnswer.length() > 0) {
                            correctAnswer.append(",");
                        }
                        correctAnswer.append(optionLabel);
                    }
                }

                // 创建答案对象
                QuestionAnswer answer = new QuestionAnswer();
                answer.setQuestionId(questionId);
                answer.setAnswer(correctAnswer.toString());
                questionAnswerMapper.insert(answer);
            }
        } else {
            // 4. 判断题和简答题：直接保存答案
            QuestionAnswer answer = question.getAnswer();
            log.info("==============================保存答案：{}", answer);
            if (answer != null) {
                answer.setQuestionId(questionId);
                questionAnswerMapper.insert(answer);
            }
        }

        return question;
    }

    /**
     * 修改题目
     * @param question 题目信息
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Question updateQuestionWithDetails(Question question) {
        Long questionId = question.getId();

        //1. 判断重复：更新的题目名称不能与除自己外已有的题目名称重复
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getType, question.getType())
                .eq(Question::getTitle, question.getTitle())
                .ne(Question::getId, questionId); // 排除当前题目本身
        Long count = this.count(wrapper);

        if (count > 0) {
            throw new RuntimeException("该类型下已存在相同名称的题目：" + question.getTitle());
        }


        // 2. 更新题目主表信息
        boolean updated = this.updateById(question);
        if (!updated) {
            throw new RuntimeException("题目更新失败");
        }

        // 3. 删除原有选项
        LambdaQueryWrapper<QuestionChoice> choiceWrapper = new LambdaQueryWrapper<>();
        choiceWrapper.eq(QuestionChoice::getQuestionId, questionId);
        questionChoiceMapper.delete(choiceWrapper);

        // 4. 重新插入新的选项（如果有）
        if (question.getChoices() != null && !question.getChoices().isEmpty()) {
            for (int i = 0; i < question.getChoices().size(); i++) {
                QuestionChoice choice = question.getChoices().get(i);
                choice.setId(null);
                choice.setQuestionId(questionId);
                choice.setSort(i); // 设置排序序号：从 0 开始递增
                questionChoiceMapper.insert(choice);
            }
        }

        // 5. 更新答案
        LambdaQueryWrapper<QuestionAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(QuestionAnswer::getQuestionId, questionId);
        QuestionAnswer existingAnswer = questionAnswerMapper.selectOne(answerWrapper);

        QuestionAnswer answer = question.getAnswer();
        if (answer != null) {
            answer.setQuestionId(questionId);
            if (existingAnswer != null) {
                // 答案存在则更新
                answer.setId(existingAnswer.getId());
                questionAnswerMapper.updateById(answer);
            } else {
                // 答案不存在则插入
                questionAnswerMapper.insert(answer);
            }
        } else if (existingAnswer != null) {
            // 如果没有提供答案但原来有答案，则删除原有答案
            questionAnswerMapper.delete(answerWrapper);
        }

        return getQuestionWithDetails(questionId);
    }

    /**
     * 删除题目
     * @param id 题目 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestionWithDetails(Long id) {
        // 1. 检查题目是否被试卷使用
        //TODO 待试卷接口实现后验证
        LambdaQueryWrapper<PaperQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PaperQuestion::getQuestionId, id);
        Long count = paperQuestionMapper.selectCount(wrapper);

        if (count > 0) {
            throw new RuntimeException("该题目已被试卷使用，无法删除");
        }

        // 2. 删除选项
        LambdaQueryWrapper<QuestionChoice> choiceWrapper = new LambdaQueryWrapper<>();
        choiceWrapper.eq(QuestionChoice::getQuestionId, id);
        questionChoiceMapper.delete(choiceWrapper);

        // 3. 删除答案
        LambdaQueryWrapper<QuestionAnswer> answerWrapper = new LambdaQueryWrapper<>();
        answerWrapper.eq(QuestionAnswer::getQuestionId, id);
        questionAnswerMapper.delete(answerWrapper);

        // 4. 删除题目
        this.removeById(id);
    }

    @Override
    public List<Question> getQuestionsByCategory(Long categoryId) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getCategoryId, categoryId)
                .orderByDesc(Question::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<Question> getQuestionsByDifficulty(String difficulty) {
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Question::getDifficulty, difficulty)
                .orderByDesc(Question::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<Question> getRandomQuestions(Integer count, Long categoryId, String difficulty) {
        // 构建查询条件
        LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();

        // 分类筛选
        if (categoryId != null) {
            wrapper.eq(Question::getCategoryId, categoryId);
        }

        // 难度筛选
        if (StringUtils.hasText(difficulty)) {
            wrapper.eq(Question::getDifficulty, difficulty);
        }

        // 查询所有符合条件的题目
        List<Question> allQuestions = this.list(wrapper);

        // 随机打乱并取指定数量
        if (allQuestions.isEmpty()) {
            return new ArrayList<>();
        }

        // 使用 Collections.shuffle 进行随机打乱
        Collections.shuffle(allQuestions);

        // 返回指定数量的题目（不超过总数）
        int actualCount = Math.min(count, allQuestions.size());
        return allQuestions.subList(0, actualCount);
    }

    /**
     * 获取最热门的题目
     * @param size 需要获取的题目数量
     * @return 热门题目列表
     */
    @Override
    public List<Question> getPopularQuestions(Integer size) {
        //准备一个集合存放题目
        List<Question> questions = new ArrayList<>();

        //1. 从redis中获取热门题目id
        Set<Object> popularQuestionIds = redisUtils.zReverseRange(CacheConstants.POPULAR_QUESTIONS_KEY, 0, size - 1);

        //2.根据id获取对应的热门题目集合
        if (popularQuestionIds != null && !popularQuestionIds.isEmpty()) {
            //longlist有序，按照热度降序
            List<Long> longList = popularQuestionIds.stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .collect(Collectors.toList());
            //不能用list查询，可能改变顺序
            for (Long questionId : longList){
                Question question = getQuestionWithDetails(questionId);
                //校验：可能redis中有题目，但数据库中没有
                if (question != null) {
                    questions.add(question);
                }
            }
        }
        //3.如果数量不足
        if (questions.size() < size) {
            int needCount = size - questions.size();
            //获取剩余题目，根据创建时间倒序
            LambdaQueryWrapper<Question> wrapper = new LambdaQueryWrapper<>();
            //排除已有 id
            List<Long> excludeIds = questions.stream()
                    .map(Question::getId)
                    .collect(Collectors.toList());
            if (!excludeIds.isEmpty()) {
                wrapper.notIn(Question::getId, excludeIds);
            }
            wrapper.orderByDesc(Question::getCreateTime);
            wrapper.last("limit " + needCount);
            List<Question> restQuestions = list(wrapper);

            List<Long> questionIds = restQuestions.stream().map(Question::getId).collect(Collectors.toList());
            for (Long questionId : questionIds){
                Question question = getQuestionWithDetails(questionId);
                questions.add(question);
            }
        }
        return questions;
    }

    @Override
    public List<QuestionImportVo> parseExcel(MultipartFile file) throws IOException {
        if (file == null) {
            throw new RuntimeException("文件不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw new RuntimeException("文件格式错误");
        }
        List<QuestionImportVo> questionImportVoList = ExcelUtil.parseExcel(file);
        return questionImportVoList;
    }

    @Override
    public int importQuestions(List<QuestionImportVo> questionImportVoList) {
        int count = 0;
        if (questionImportVoList == null){
            throw new RuntimeException("导入数据不能为空");
        }
        for (QuestionImportVo importVo : questionImportVoList){
            try {
                System.out.println("====================="+importVo);
                Question question = questionImportVoToQuestion(importVo);
                createQuestionWithDetails(question);
                count++;
            }catch (Exception e){
                log.error("导入题目失败", e);
            }
        }
        return count;
    }

    /**
     * 将 QuestionImportVo 转换为 Question 实体
     * @param importVo 导入 VO
     * @return Question 实体
     */
    private Question questionImportVoToQuestion(QuestionImportVo importVo) {
        Question question = new Question();

        // 基本信息
        question.setTitle(importVo.getTitle());
        question.setType(importVo.getType());
        question.setMulti(importVo.getMulti() != null ? importVo.getMulti() : false);
        question.setCategoryId(importVo.getCategoryId() != null ? importVo.getCategoryId() : 1L);
        question.setDifficulty(importVo.getDifficulty() != null ? importVo.getDifficulty() : "MEDIUM");
        question.setScore(importVo.getScore() != null ? importVo.getScore() : 5);
        question.setAnalysis(importVo.getAnalysis());

        // 设置选项（仅选择题）
        if ("CHOICE".equals(importVo.getType()) && importVo.getChoices() != null) {
            List<QuestionChoice> choices = new ArrayList<>();
            for (QuestionImportVo.ChoiceImportDto choiceDto : importVo.getChoices()) {
                QuestionChoice choice = new QuestionChoice();
                choice.setContent(choiceDto.getContent());
                choice.setIsCorrect(choiceDto.getIsCorrect());
                choice.setSort(choiceDto.getSort());
                choices.add(choice);
            }
            question.setChoices(choices);

        } else {
            // 判断题和简答题：需要给answer字段赋值
            if (importVo.getAnswer() != null && !importVo.getAnswer().isEmpty()) {
                QuestionAnswer answer = new QuestionAnswer();
                answer.setAnswer(importVo.getAnswer());
                if ("TEXT".equals(importVo.getType())) {
                    answer.setKeywords(importVo.getKeywords());
                }
                question.setAnswer(answer);
            }
        }

        return question;
    }

    /**
     * ai生成题目
     * @param request
     * @return
     * @throws InterruptedException
     */
    @Override
    public List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) throws InterruptedException {
        //1.生成提示词
        String prompt = qwenAiService.buildPrompt(request);
        //2.获取ai模型返回结果
        String response = qwenAiService.callQwenAi(prompt);
        //3.解析结果
        //3.1判定开始（'''json）和结束位置（'''）
        int startIndex = response.indexOf("```json");
        int endIndex = response.lastIndexOf("```");
        if (startIndex != 1 && endIndex != -1 && startIndex < endIndex) {
            //结构正确
            String json = response.substring(startIndex + 7, endIndex);
            JSONObject jsonObject = JSONObject.parseObject(json);
            JSONArray questions = jsonObject.getJSONArray("questions");
            List<QuestionImportVo> questionImportVoList = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++){
                JSONObject questionJson = questions.getJSONObject(i);
                QuestionImportVo questionImportVo = new QuestionImportVo();
                questionImportVo.setTitle(questionJson.getString("title"));
                questionImportVo.setType(questionJson.getString("type"));
                questionImportVo.setMulti(questionJson.getBoolean("multi"));
                questionImportVo.setCategoryId(request.getCategoryId());
                questionImportVo.setDifficulty(questionJson.getString("difficulty"));
                questionImportVo.setScore(questionJson.getInteger("score"));
                questionImportVo.setAnswer(questionJson.getString("answer"));
                log.info("==========================answer=================:{}", questionImportVo.getAnswer());
                questionImportVo.setKeywords(questionJson.getString("analysis"));
                //选择题选项
                if ("CHOICE".equals(questionImportVo.getType())){
                    JSONArray choices = questionJson.getJSONArray("choices");
                    if (choices != null && !choices.isEmpty()) {
                        List<QuestionImportVo.ChoiceImportDto> choiceImportDtos = new ArrayList<>();
                        for (int j = 0; j < choices.size(); j++) {
                            JSONObject choiceJson = choices.getJSONObject(j);
                            QuestionImportVo.ChoiceImportDto choiceImportDto = new QuestionImportVo.ChoiceImportDto();
                            choiceImportDto.setContent(choiceJson.getString("content"));
                            choiceImportDto.setIsCorrect(choiceJson.getBoolean("isCorrect"));
                            choiceImportDto.setSort(choiceJson.getInteger("sort"));
                            choiceImportDtos.add(choiceImportDto);
                        }
                        questionImportVo.setChoices(choiceImportDtos);
                    }
                }
                questionImportVoList.add(questionImportVo);
            }
            return questionImportVoList;
        }

        throw new RuntimeException("数据结构错误，内容为：%s".formatted(response));
    }


}
