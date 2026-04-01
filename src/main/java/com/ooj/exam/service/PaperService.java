package com.ooj.exam.service;

import com.ooj.exam.entity.Paper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ooj.exam.vo.PaperVo;

/**
 * 试卷服务接口
 */
public interface PaperService extends IService<Paper> {
    /**
     * 创建试卷
     * @param paperVo 试卷数据传输对象
     * @return 创建后的试卷
     */
    Paper createPaper(PaperVo paperVo);

    /**
     * 更新试卷
     * @param id 试卷 ID
     * @param paperVo 试卷数据传输对象
     * @return 更新后的试卷
     */
    Paper updatePaper(Integer id, PaperVo paperVo);

    /**
     * 更新试卷状态
     * @param id 试卷 ID
     * @param status 新状态
     */
    void updatePaperStatus(Integer id, String status);

    /**
     * 获取试卷详情（包含题目）
     * @param id 试卷 ID
     * @return 试卷详情
     */
    Paper getPaperDetail(Integer id);
} 