package com.ooj.exam.service;

import com.ooj.exam.entity.Question;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ooj.exam.vo.AiGenerateRequestVo;
import com.ooj.exam.vo.QuestionImportVo;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 题目业务服务接口 - 定义题目相关的业务逻辑
 *
 * Spring Boot 三层架构教学要点：
 * 1. Service 层：业务逻辑层，位于 Controller 和 Mapper 之间
 * 2. 接口设计：定义业务方法规范，便于不同实现类的切换
 * 3. 继承 IService：使用 MyBatis Plus 提供的通用服务接口，减少重复代码
 * 4. 事务管理：Service 层是事务的边界，复杂业务操作应该加@Transactional
 * 5. 业务封装：将复杂的数据操作封装成有业务意义的方法
 *
 * MyBatis Plus 教学：
 * - IService<T>：提供基础的 CRD 方法（save、update、remove、list 等）
 * - 自定义方法：在接口中定义特定业务需求的方法
 * - 实现类：继承 ServiceImpl<Mapper, Entity>并实现自定义业务方法
 *
 * 设计原则：
 * - 单一职责：专门处理题目相关的业务逻辑
 * - 开闭原则：通过接口定义，便于扩展新的实现
 * - 依赖倒置：Controller 依赖接口而不是具体实现
 *
 * @author 智能学习平台开发团队
 * @version 1.0
 */
public interface QuestionService extends IService<Question> {

    /**
     * 分页查询题目列表（支持多条件筛选）
     *
     * @param page 当前页码
     * @param size 每页大小
     * @param categoryId 分类 ID（可选）
     * @param difficulty 难度（可选）
     * @param type 题型（可选）
     * @param keyword 关键词（可选）
     * @return 分页结果
     */
    Page<Question> getQuestionList(Integer page, Integer size, Long categoryId, String difficulty, String type, String keyword);

    /**
     * 根据 ID 查询题目详情（包含选项和答案）
     *
     * @param id 题目 ID
     * @return 题目详情
     */
    Question getQuestionWithDetails(Long id);

    /**
     * 创建题目（包含选项和答案）
     *
     * @param question 题目信息
     * @return 创建后的题目
     */
    Question createQuestionWithDetails(Question question);

    /**
     * 更新题目（包含选项和答案）
     *
     * @param question 题目信息
     * @return 更新后的题目
     */
    Question updateQuestionWithDetails(Question question);

    /**
     * 删除题目（级联删除选项和答案）
     *
     * @param id 题目 ID
     */
    void deleteQuestionWithDetails(Long id);

    /**
     * 根据分类查询题目列表
     *
     * @param categoryId 分类 ID
     * @return 题目列表
     */
    List<Question> getQuestionsByCategory(Long categoryId);

    /**
     * 根据难度查询题目列表
     *
     * @param difficulty 难度
     * @return 题目列表
     */
    List<Question> getQuestionsByDifficulty(String difficulty);

    /**
     * 随机获取题目
     *
     * @param count 题目数量
     * @param categoryId 分类 ID（可选）
     * @param difficulty 难度（可选）
     * @return 随机题目列表
     */
    List<Question> getRandomQuestions(Integer count, Long categoryId, String difficulty);

    List<Question> getPopularQuestions(Integer size);

    List<QuestionImportVo> parseExcel(MultipartFile file) throws IOException;

    /**
     * 批量导入题目
     * @param questions
     * @return
     */
    int importQuestions(List<QuestionImportVo> questions);

    /**
     * 使用AI生成题目
     * @param request
     * @return
     */
    List<QuestionImportVo> aiGenerateQuestions(AiGenerateRequestVo request) throws InterruptedException;
}
