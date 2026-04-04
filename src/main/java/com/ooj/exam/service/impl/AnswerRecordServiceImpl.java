package com.ooj.exam.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.entity.AnswerRecord;
import com.ooj.exam.mapper.AnswerRecordMapper;
import com.ooj.exam.service.AnswerRecordService;
import org.springframework.stereotype.Service;

@Service
public class AnswerRecordServiceImpl extends ServiceImpl<AnswerRecordMapper, AnswerRecord> implements AnswerRecordService {
}
