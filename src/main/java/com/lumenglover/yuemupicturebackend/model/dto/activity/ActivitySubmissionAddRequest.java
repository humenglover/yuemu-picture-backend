package com.lumenglover.yuemupicturebackend.model.dto.activity;

import lombok.Data;

import java.io.Serializable;

/**
 * 活动提交请求
 */
@Data
public class ActivitySubmissionAddRequest implements Serializable {

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 提交标题
     */
    private String submissionTitle;

    /**
     * 提交描述
     */
    private String submissionDesc;

    private static final long serialVersionUID = 1L;
}
