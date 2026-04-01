package com.ooj.exam.service;

import com.ooj.exam.entity.Category;

import java.util.List;

public interface CategoryService {


    /**
     * 获取分类列表（包含题目数量）
     * @return 分类列表数据
     */
    List<Category> getCategories();

    /**
     * 获取分类树形结构
     * @return 分类树数据
     */
    List<Category> getCategoryTree();

    /**
     * 添加分类
     * @param category 分类对象
     */
    void addCategory(Category category);

    /**
     * 更新分类
     * @param category 分类对象
     */
    void updateCategory(Category category);

    /**
     * 删除分类
     * @param id 分类 ID
     */
    void deleteCategory(Long id);
}