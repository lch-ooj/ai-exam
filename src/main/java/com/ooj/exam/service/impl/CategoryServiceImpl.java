package com.ooj.exam.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ooj.exam.entity.Category;
import com.ooj.exam.entity.Question;
import com.ooj.exam.exception.GlobalExceptionHandler;
import com.ooj.exam.mapper.CategoryMapper;
import com.ooj.exam.mapper.QuestionMapper;
import com.ooj.exam.service.CategoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Autowired
    private QuestionMapper questionMapper;

    @Override
    public List<Category> getCategories() {
        // 获取所有分类
        List<Category> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );

        // 获取并填充每个分类的题目数量
        fillQuestionCount(categories);
        return categories;
    }

    @Override
    public List<Category> getCategoryTree() {
        // 获取所有分类
        List<Category> allCategories = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>()
                        .orderByAsc(Category::getSort)
        );

        // 获取并填充每个分类的题目数量
        fillQuestionCount(allCategories);

        // 构建树形结构
        return buildTree(allCategories);
    }

    @Override
    public void addCategory(Category category) {
        // 验证父级分类是否存在
        if (category.getParentId() != null && category.getParentId() > 0) {
            Category parentCategory = categoryMapper.selectById(category.getParentId());
            if (parentCategory == null) {
                throw new RuntimeException("父级分类不存在");
            }
        }

        // 检查同级分类名称是否重复
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, category.getName())
                        .eq(Category::getParentId, category.getParentId() == null ? 0 : category.getParentId())
        );
        if (count > 0) {
            throw new RuntimeException("同级分类下已存在相同名称的分类");
        }

        // 设置默认值
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSort() == null) {
            category.setSort(0);
        }

        categoryMapper.insert(category);
        log.info("分类添加成功，ID: {}", category.getId());
    }

    @Override
    public void updateCategory(Category category) {
        Category existingCategory = categoryMapper.selectById(category.getId());
        if (existingCategory == null) {
            throw new RuntimeException("分类不存在");
        }

        // 验证父级分类
        if (category.getParentId() != null && category.getParentId() > 0) {
            // 不能将自己设为父级分类
            if (category.getParentId().equals(category.getId())) {
                throw new RuntimeException("不能将自己设为父级分类");
            }

            // 验证父级分类是否存在
            Category parentCategory = categoryMapper.selectById(category.getParentId());
            if (parentCategory == null) {
                throw new RuntimeException("父级分类不存在");
            }
        }

        // 检查同级分类名称是否重复
        Long count = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getName, category.getName())
                        .eq(Category::getParentId, category.getParentId() == null ? 0 : category.getParentId())
                        .ne(Category::getId, category.getId())
        );
        if (count > 0) {
            throw new RuntimeException("同级分类下已存在相同名称的分类");
        }

        categoryMapper.updateById(category);
        log.info("分类更新成功，ID: {}", category.getId());
    }

    @Override
    public void deleteCategory(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new RuntimeException("分类不存在");
        }

        // 检查是否有子分类
        Long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<Category>()
                        .eq(Category::getParentId, id)
        );
        if (childCount > 0) {
            throw new RuntimeException("该分类下有子分类，无法删除");
        }

        // 检查是否有题目
        Long questionCount = questionMapper.selectCount(
                new LambdaQueryWrapper<Question>()
                        .eq(Question::getCategoryId, id)
        );
        if (questionCount > 0) {
            throw new RuntimeException("该分类下有题目，无法删除");
        }

        categoryMapper.deleteById(id);
        log.info("分类删除成功，ID: {}", id);
    }

    /**
     * 填充分类的题目数量
     */
    private void fillQuestionCount(List<Category> categories) {
        // 获取每个分类的题目数量
        List<Map<String, Object>> questionCountList = categoryMapper.getCategoryQuestionCount();

        // 将结果转换为 Map<Long, Long> 格式
        Map<Long, Long> questionCountMap = questionCountList.stream()
                .collect(Collectors.toMap(
                        map -> Long.valueOf(map.get("category_id").toString()),
                        map -> Long.valueOf(map.get("question_count").toString())
                ));
        // 设置题目数量
        categories.forEach(category ->
                category.setCount(questionCountMap.getOrDefault(category.getId(), 0L))
        );
    }

    /**
     * 构建树形结构
     */
    private List<Category> buildTree(List<Category> categories) {
        // 按 parentId 分组
        Map<Long, List<Category>> childrenMap = categories.stream()
                .collect(Collectors.groupingBy(Category::getParentId));

        // 设置 children 属性，并从下至上汇总题目数量
        categories.forEach(category -> {
            List<Category> children = childrenMap.getOrDefault(category.getId(), new ArrayList<>());
            category.setChildren(children);

            // 汇总子分类的题目数量到父分类
            long childrenCount = children.stream()
                    .mapToLong(c -> c.getCount() != null ? c.getCount() : 0L)
                    .sum();
            long selfCount = category.getCount() != null ? category.getCount() : 0L;
            category.setCount(selfCount + childrenCount);
        });

        // 返回顶级分类（parentId = 0）
        return categories.stream()
                .filter(c -> c.getParentId() == 0)
                .collect(Collectors.toList());
    }
}
