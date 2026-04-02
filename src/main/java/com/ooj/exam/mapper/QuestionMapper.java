package com.ooj.exam.mapper;


import com.ooj.exam.entity.Question;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 题目Mapper接口
 * 继承MyBatis Plus的BaseMapper，提供基础的CRUD操作
 */
public interface QuestionMapper extends BaseMapper<Question> {
    /**
     * 根据试卷id查询题目集合
     * @param paperId
     * @return
     */
    List<Question> customQueryQuestionListByPaperId(Integer paperId);
} 