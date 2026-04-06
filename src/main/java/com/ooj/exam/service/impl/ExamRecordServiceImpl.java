package com.ooj.exam.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ooj.exam.entity.AnswerRecord;
import com.ooj.exam.entity.ExamRecord;
import com.ooj.exam.entity.Paper;
import com.ooj.exam.mapper.ExamRecordMapper;
import com.ooj.exam.service.AnswerRecordService;
import com.ooj.exam.service.ExamRecordService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ooj.exam.service.PaperService;
import com.ooj.exam.vo.ExamRankingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 考试记录Service实现类
 * 实现考试记录相关的业务逻辑
 */
@Slf4j
@Service
public class ExamRecordServiceImpl extends ServiceImpl<ExamRecordMapper, ExamRecord> implements ExamRecordService {

    @Autowired
    private PaperService paperService;
    @Autowired
    private AnswerRecordService answerRecordService;
    @Autowired
    private ExamRecordMapper examRecordMapper;

    /**
     * 分页查询考试记录
     * @param examRecordPage
     * @param studentName
     * @param status
     * @param startDate
     * @param endDate
     */
    @Override
    public void  getExamRecordsByPage(Page<ExamRecord> examRecordPage, String studentName, Integer status, String startDate, String endDate) {
        //1.正常对考试记录进行分页查询
        QueryWrapper<ExamRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.like(studentName != null, "student_name", studentName);
        if (status != null){
            String examStatus = switch (status){
                case 0 -> "进行中";
                case 1 -> "已完成";
                case 2 -> "已批阅";
                default -> null;
            };
            queryWrapper.eq("exam_status", examStatus);
        }
        queryWrapper.between(startDate != null && endDate != null, "exam_time", startDate, endDate);
        //对考试记录单表分页查询
        page(examRecordPage, queryWrapper);
        List<ExamRecord> examRecordList = examRecordPage.getRecords();
        if (examRecordList == null){
            log.info("考试记录为空");
            return;
        }
        //2.查询每条考试记录对应的试卷信息
        for (ExamRecord examRecord : examRecordList) {
            //3.赋值
            examRecord.setPaper(paperService.getPaperById(examRecord.getExamId()));
        }
    }

    /**
     * 删除考试记录（会检查是否有关联的答题记录）
     * @param id 考试记录ID
     * @return 删除结果消息
     * @throws IllegalArgumentException 如果存在关联数据或记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeExamRecordById(Integer id) {
        // 1. 检查考试记录是否存在
        ExamRecord examRecord = this.getById(id);
        if (examRecord == null) {
            log.warn("尝试删除不存在的考试记录，ID: {}", id);
            throw new RuntimeException("考试记录不存在");
        }

        //2.状态判断：进行中的考试记录不能删除（除非已超过考试时间）
        if ("进行中".equals(examRecord.getStatus())) {
            // 获取试卷信息，检查是否超过考试时间
            Paper paper = paperService.getPaperById(examRecord.getExamId());
            if (paper != null && paper.getDuration() != null) {
                // 计算考试应该结束的时间 = 开始时间 + 考试时长（分钟）
                LocalDateTime shouldEndTime = examRecord.getStartTime().plusMinutes(paper.getDuration());

                // 如果当前时间还未超过应该结束的时间，则不允许删除
                if (LocalDateTime.now().isBefore(shouldEndTime)) {
                    log.warn("尝试删除未超时的进行中考试记录，ID: {}, 应结束时间: {}", id, shouldEndTime);
                    throw new RuntimeException("正在进行中的考试不能删除（距离考试结束还有时间）");
                }

                log.info("考试已超时，允许删除进行中的考试记录，ID: {}, 应结束时间: {}, 当前时间: {}",
                        id, shouldEndTime, LocalDateTime.now());
            } else {
                log.warn("无法获取试卷或考试时长信息，ID: {}, examId: {}", id, examRecord.getExamId());
                throw new RuntimeException("无法获取试卷信息，删除失败");
            }
        }

        // 3. 删除关联的答题记录
        QueryWrapper<AnswerRecord> answerQueryWrapper = new QueryWrapper<>();
        answerQueryWrapper.eq("exam_record_id", id);
        answerRecordService.remove(answerQueryWrapper);
        log.info("删除考试记录的关联答题记录，ID: {}", id);

        // 4. 删除考试记录
        boolean removed = this.removeById(id.longValue());
        if (removed) {
            log.info("成功删除考试记录，ID: {}, 考生: {}", id, examRecord.getStudentName());
        } else {
            log.error("删除考试记录失败，ID: {}", id);
            throw new RuntimeException("删除考试记录失败");
        }
    }

    @Override
    public List<ExamRankingVO> getExamRanking(Integer paperId, Integer limit) {
        List<ExamRankingVO> examRankingList = examRecordMapper.getExamRanking(paperId, limit);
        System.out.println("examRankingList = " + examRankingList);
        return examRankingList;
    }
}