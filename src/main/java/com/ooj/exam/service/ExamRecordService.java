package com.ooj.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ooj.exam.entity.ExamRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ooj.exam.vo.ExamRankingVO;


import java.util.List;

/**
 * 考试记录Service接口
 * 定义考试记录相关的业务方法
 */
public interface ExamRecordService extends IService<ExamRecord> {

    /**
     * 分页查询考试记录
     * @param examRecordPage
     * @param studentName
     * @param status
     * @param startDate
     * @param endDate
     */
    void getExamRecordsByPage(Page<ExamRecord> examRecordPage, String studentName, Integer status, String startDate, String endDate);

    /**
     * 删除考试记录
     * @param id
     */
    void removeExamRecordById(Integer id);

    /**
     * 获取考试排名
     * @param paperId
     * @param limit
     * @return
     */
    List<ExamRankingVO> getExamRanking(Integer paperId, Integer limit);
}