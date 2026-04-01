package com.ooj.exam.mapper;

import com.ooj.exam.entity.PaperQuestion;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @description 针对表【paper_question(试卷-题目关联表)】的数据库操作Mapper
* @createDate 2025-06-20 22:37:43
* @Entity com.exam.entity.PaperQuestion
*/
@Mapper
public interface PaperQuestionMapper extends BaseMapper<PaperQuestion> {
    /**
     * 批量插入试卷 - 题目关联记录
     * @param paperQuestions 试卷 - 题目关联列表
     * @return 插入的记录数
     */
    int insertBatch(@Param("list") List<PaperQuestion> paperQuestions);
} 