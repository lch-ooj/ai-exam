package com.ooj.exam.controller;

import com.ooj.exam.common.CacheConstants;
import com.ooj.exam.common.Result;
import com.ooj.exam.entity.Question;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ooj.exam.service.QuestionService;
import com.ooj.exam.utils.RedisUtils;
import com.ooj.exam.vo.QuestionQueryVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 题目控制器 - 处理题目相关的 HTTP 请求
 *
 * Spring MVC 架构教学要点：
 * 1. Controller 层：负责接收 HTTP 请求，调用 Service 层处理业务逻辑，返回响应
 * 2. RESTful API 设计：使用 HTTP 方法语义（GET 查询、POST 创建、PUT 更新、DELETE 删除）
 * 3. 依赖注入：通过@Autowired 注解注入 Service 和 Mapper 依赖
 * 4. 请求映射：通过@RequestMapping 系列注解映射 URL 路径到处理方法
 * 5. 参数绑定：自动将 HTTP 请求参数绑定到方法参数
 * 6. 数据返回：统一使用 Result 包装返回数据，便于前端处理
 *
 * 业务功能：
 * - 题目的 CRUD 操作（创建、查询、更新、删除）
 * - 多条件筛选和分页查询
 * - 随机题目获取（用于自动组卷）
 * - 热门题目展示（用于首页推荐）
 *
 * @author 智能学习平台开发团队
 * @version 1.0
 */
@RestController  // @Controller + @ResponseBody，表示这是一个 REST 控制器，返回 JSON 数据
@RequestMapping("/api/questions")  // 设置基础 URL 路径，所有方法的 URL 都以此开头
 // 允许跨域访问，解决前后端分离开发中的跨域问题
@Tag(name = "题目管理", description = "题目相关的增删改查操作，包括分页查询、随机获取、热门推荐等功能")  // Swagger 标签，用于分组显示 API
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    /**
     * 分页查询题目列表（支持多条件筛选）
     *
     * RESTful API 教学：
     * - URL：GET /api/questions/list
     * - 查询参数：通过@RequestParam 接收 URL 查询参数
     * - 默认值：通过 defaultValue 设置参数默认值
     * - 可选参数：通过 required = false 设置可选参数
     *
     * MyBatis Plus 分页教学：
     * - Page 对象：封装分页信息（页码、每页大小、总数等）
     * - QueryWrapper：动态构建查询条件，避免 SQL 注入
     * - 条件构建：支持等值查询 (eq)、模糊查询 (like)、排序 (orderBy)
     *
     * @param page 当前页码，从 1 开始，默认第 1 页
     * @param size 每页显示数量，默认 10 条
     * @param questionQueryVo 查询条件参数对象
     * @return 封装的分页查询结果，包含题目列表和分页信息
     */
    @GetMapping("/list")  // 映射GET请求到/api/questions/list
    @Operation(summary = "分页查询题目列表", description = "支持按分类、难度、题型、关键词进行多条件筛选的分页查询")  // Swagger接口描述
    public Result<Page<Question>> getQuestionList(
            @Parameter(description = "当前页码，从1开始", example = "1") @RequestParam(defaultValue = "1") Integer page,  // 参数描述
            @Parameter(description = "每页显示数量", example = "10") @RequestParam(defaultValue = "10") Integer size,
            QuestionQueryVo questionQueryVo) {

        Page<Question> result = questionService.getQuestionList(page, size, questionQueryVo.getCategoryId(),questionQueryVo.getDifficulty(),questionQueryVo.getType(),questionQueryVo.getKeyword());
        return Result.success(result);

    }

    /**
     * 根据 ID 查询单个题目详情
     *
     * RESTful API 教学：
     * - URL 模式：GET /api/questions/{id}
     * - 路径参数：通过@PathVariable 获取 URL 中的参数
     * - 语义化：URL 直观表达资源和操作
     *
     * @param id 题目主键 ID，通过 URL 路径传递
     * @return 题目详细信息，包含选项和答案
     */
    @GetMapping("/{id}")  // {id}是路径变量，会映射到方法参数
    @Operation(summary = "根据 ID 查询题目详情", description = "获取指定 ID 的题目完整信息，包括题目内容、选项、答案等详细数据")  // API 描述
    public Result<Question> getQuestionById(
            @Parameter(description = "题目 ID", example = "1") @PathVariable Long id) {
        try {
            Question question = questionService.getQuestionWithDetails(id);
            if (question == null) {
                return Result.error("题目不存在");
            }
            return Result.success(question);
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 创建新题目
     *
     * RESTful API 教学：
     * - HTTP 方法：POST 表示创建操作
     * - 请求体：通过@RequestBody 接收 JSON 格式的请求体
     * - 数据绑定：Spring 自动将 JSON 转换为 Java 对象
     *
     * 事务处理：
     * - 题目创建涉及多张表（题目、选项、答案）
     * - Service 层方法应该使用@Transactional 保证数据一致性
     *
     * @param question 前端提交的题目数据（JSON 格式）
     * @return 创建成功后的题目信息
     */
    @PostMapping  // 映射 POST 请求到/api/questions
    @Operation(summary = "创建新题目", description = "添加新的考试题目，支持选择题、判断题、简答题等多种题型")  // API 描述
    public Result<Question> createQuestion(@RequestBody Question question) {
        try {
            Question saved = questionService.createQuestionWithDetails(question);
            return Result.success(saved);
        } catch (Exception e) {
            return Result.error("创建失败：" + e.getMessage());
        }
    }

    /**
     * 更新题目信息
     *
     * RESTful API 教学：
     * - HTTP 方法：PUT 表示更新操作
     * - URL 设计：PUT /api/questions/{id} 语义明确
     * - 参数组合：路径参数 (ID) + 请求体 (数据)
     *
     * @param id 要更新的题目 ID
     * @param question 更新的题目数据
     * @return 更新后的题目信息
     */
    @PutMapping("/{id}")  // 处理 PUT 请求
    @Operation(summary = "更新题目信息", description = "修改指定题目的内容、选项、答案等信息")  // API 描述
    public Result<Question> updateQuestion(
            @Parameter(description = "题目 ID") @PathVariable Long id,
            @RequestBody Question question) {
        try {
            question.setId(id);
            Question updated = questionService.updateQuestionWithDetails(question);
            return Result.success(updated);
        } catch (Exception e) {
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    /**
     * 删除题目
     *
     * RESTful API 教学：
     * - HTTP 方法：DELETE 表示删除操作
     * - 响应设计：删除成功返回确认消息，失败返回错误信息
     *
     * 注意事项：
     * - 删除前应检查题目是否被试卷引用
     * - 考虑使用逻辑删除而非物理删除，保留数据完整性
     *
     * @param id 要删除的题目 ID
     * @return 删除操作结果
     */
    @DeleteMapping("/{id}")  // 处理 DELETE 请求
    @Operation(summary = "删除题目", description = "根据 ID 删除指定的题目，包括关联的选项和答案数据")  // API 描述
    public Result<String> deleteQuestion(
            @Parameter(description = "题目 ID") @PathVariable Long id) {
        try {
            questionService.deleteQuestionWithDetails(id);
            return Result.success("题目删除成功");
        } catch (Exception e) {
            return Result.error("题目删除失败：" + e.getMessage());
        }
    }

    /**
     * 根据分类查询题目列表
     *
     * 业务场景：题目管理时按分类浏览，组卷时按分类选择题目
     *
     * @param categoryId 分类 ID
     * @return 该分类下的所有题目
     */
    @GetMapping("/category/{categoryId}")  // 处理 GET 请求
    @Operation(summary = "按分类查询题目", description = "获取指定分类下的所有题目列表")  // API 描述
    public Result<List<Question>> getQuestionsByCategory(
            @Parameter(description = "分类 ID") @PathVariable Long categoryId) {
        try {
            List<Question> questions = questionService.getQuestionsByCategory(categoryId);
            return Result.success(questions);
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 根据难度查询题目列表
     *
     * 业务场景：按难度筛选题目，支持分层次教学
     *
     * @param difficulty 难度等级（EASY/MEDIUM/HARD）
     * @return 指定难度的题目列表
     */
    @GetMapping("/difficulty/{difficulty}")  // 处理 GET 请求
    @Operation(summary = "按难度查询题目", description = "获取指定难度等级的题目列表")  // API 描述
    public Result<List<Question>> getQuestionsByDifficulty(
            @Parameter(description = "难度等级，可选值：EASY(简单)/MEDIUM(中等)/HARD(困难)") @PathVariable String difficulty) {
        try {
            List<Question> questions = questionService.getQuestionsByDifficulty(difficulty);
            return Result.success(questions);
        } catch (Exception e) {
            return Result.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 随机获取题目 - 智能组卷核心功能
     *
     * 算法思路：
     * 1. 根据条件筛选候选题目池
     * 2. 使用数据库 RAND() 函数或 Java 随机算法
     * 3. 保证题目不重复
     *
     * 业务价值：
     * - 自动组卷：减少教师工作量
     * - 防止作弊：每次考试题目不同
     * - 个性化：根据难度和分类定制
     *
     * @param count 需要的题目数量，默认 10 题
     * @param categoryId 限定分类，可选
     * @param difficulty 限定难度，可选
     * @return 随机选择的题目列表
     */
    @GetMapping("/random")  // 处理 GET 请求
    @Operation(summary = "随机获取题目", description = "按指定条件随机抽取题目，用于智能组卷功能")  // API 描述
    public Result<List<Question>> getRandomQuestions(
            @Parameter(description = "需要获取的题目数量", example = "10") @RequestParam(defaultValue = "10") Integer count,
            @Parameter(description = "分类 ID 限制条件，可选") @RequestParam(required = false) Long categoryId,
            @Parameter(description = "难度限制条件，可选值：EASY/MEDIUM/HARD") @RequestParam(required = false) String difficulty) {
        try {
            List<Question> questions = questionService.getRandomQuestions(count, categoryId, difficulty);
            return Result.success(questions);
        } catch (Exception e) {
            return Result.error("获取随机题目失败：" + e.getMessage());
        }
    }

    /**
     * 获取热门题目 - 首页展示推荐题目
     *
     * 业务逻辑：
     * - 热门度定义：按创建时间倒序，展示最新题目
     * - 可扩展：未来可按答题次数、正确率等指标排序
     *
     * SQL 优化教学：
     * - 使用 LIMIT 限制结果集大小，提高查询性能
     * - 建议在 create_time 字段上建立索引
     *
     * @param size 返回题目数量，默认 6 条（适合首页展示）
     * @return 热门题目列表
     */
    /**
     * 获取热门题目 - 基于访问次数的推荐
     *
     * 业务逻辑：
     * - 热门度定义：根据题目被访问的次数排序
     * - 实现方式：使用 Redis Sorted Set 记录访问次数
     * - 数据来源：用户查看题目详情时自动记录
     *
     * 技术亮点：
     * - 使用 Redis Sorted Set 高效存储和排序
     * - 异步增加访问计数，不影响用户体验
     * - 缓存热门题目列表，提高查询性能
     *
     * @param size 返回题目数量，默认 10 条
     * @return 热门题目列表
     */
    @GetMapping("/popular")
    @Operation(summary = "获取热门题目", description = "获取访问次数最多的热门题目，用于首页推荐展示")
    public Result<List<Question>> getPopularQuestions(
            @Parameter(description = "返回题目数量", example = "6") @RequestParam(defaultValue = "6") Integer size) {
        List<Question> questions = questionService.getPopularQuestions(size);

        return Result.success(questions);
    }

    /**
     * 刷新热门题目缓存 - 管理员功能
     *
     * 业务场景：
     * - 系统初始化：首次部署时初始化热门题目数据
     * - 数据重置：清除历史访问记录，重新开始统计
     * - 手动干预：管理员可以强制更新热门题目排名
     *
     * 技术实现：
     * - 清除缓存：删除 Redis 中的访问计数数据
     * - 重建缓存：为所有题目设置初始访问计数
     * - 权限控制：仅管理员可操作（前端负责控制）
     *
     * @return 刷新结果，包含处理的题目数量
     */
    @PostMapping("/popular/refresh")
    @Operation(summary = "刷新热门题目缓存", description = "管理员功能，重置或初始化热门题目的访问计数")
    public Result<Integer> refreshPopularQuestions() {
        try {
            // 临时实现：返回题目总数
            long count = questionService.count();
            return Result.success((int) count);
        } catch (Exception e) {
            return Result.error("刷新热门题目缓存失败：" + e.getMessage());
        }
    }

}
