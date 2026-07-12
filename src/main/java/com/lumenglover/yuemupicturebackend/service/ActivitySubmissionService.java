package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivitySubmissionAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivitySubmissionQueryRequest;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivitySubmissionReviewRequest;
import com.lumenglover.yuemupicturebackend.model.entity.ActivitySubmission;
import com.lumenglover.yuemupicturebackend.model.vo.ActivitySubmissionVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 活动提交服务
 */
public interface ActivitySubmissionService extends IService<ActivitySubmission> {

    /**
     * 提交图片到活动
     */
    Long submitToActivity(ActivitySubmissionAddRequest request, HttpServletRequest httpRequest);

    /**
     * 审核提交
     */
    Boolean reviewSubmission(ActivitySubmissionReviewRequest request, HttpServletRequest httpRequest);

    /**
     * 获取查询条件
     */
    QueryWrapper<ActivitySubmission> getQueryWrapper(ActivitySubmissionQueryRequest request);

    /**
     * 获取提交VO分页
     */
    Page<ActivitySubmissionVO> getSubmissionVOPage(Page<ActivitySubmission> submissionPage, HttpServletRequest httpRequest);

    /**
     * 获取提交VO
     */
    ActivitySubmissionVO getSubmissionVO(ActivitySubmission submission, HttpServletRequest httpRequest);
}
