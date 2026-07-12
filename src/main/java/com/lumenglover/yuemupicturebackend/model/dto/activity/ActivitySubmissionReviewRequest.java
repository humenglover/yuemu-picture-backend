package com.lumenglover.yuemupicturebackend.model.dto.activity;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动提交审核请求
 */
@Data
public class ActivitySubmissionReviewRequest implements Serializable {

    /**
     * 提交ID
     */
    private Long id;

    /**
     * 状态：1-通过 2-拒绝
     */
    private Integer status;

    /**
     * 审核信息
     */
    private String reviewMessage;

    private static final long serialVersionUID = 1L;
}
