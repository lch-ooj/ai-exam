package com.ooj.exam.mapper;

import com.ooj.exam.entity.Category;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface CategoryMapper extends BaseMapper<Category> {

    /**
     * 获取每个分类的题目数量统计
     * @return 包含分类 ID 和题目数量的结果列表
     */
    @Select("SELECT category_id, COUNT(*) as question_count FROM questions WHERE is_deleted = 0 GROUP BY category_id")
    List<Map<String, Object>> getCategoryQuestionCount();
}