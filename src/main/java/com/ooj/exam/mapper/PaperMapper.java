package com.ooj.exam.mapper;

import com.ooj.exam.entity.Paper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ooj.exam.entity.PaperQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author 86158
* @description 针对表【paper(试卷表)】的数据库操作Mapper
* @createDate 2025-06-20 22:37:43
* @Entity com.exam.entity.Paper
*/
@Mapper
public interface PaperMapper extends BaseMapper<Paper> {

} 