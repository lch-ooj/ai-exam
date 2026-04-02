package com.ooj.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.entity.PaperQuestion;
import com.ooj.exam.mapper.PaperQuestionMapper;
import com.ooj.exam.service.PaperQuestionService;
import org.springframework.stereotype.Service;

@Service
public class PaperQuestionServiceImpl extends ServiceImpl<PaperQuestionMapper, PaperQuestion> implements PaperQuestionService {
}
