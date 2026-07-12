package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 活动提交视图对象
 */
@Data
public class ActivitySubmissionVO implements Serializable {

    /**
     * 提交ID
     */
    private Long id;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 提交用户ID
     */
    private Long userId;

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

    /**
     * 状态：0-待审核 1-已通过 2-已拒绝
     */
    private Integer status;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 获得票数
     */
    private Integer voteCount;

    /**
     * 排名
     */
    private Integer ranking;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 用户信息
     */
    private UserVO user;

    /**
     * 图片信息
     */
    private PictureVO picture;

    /**
     * 是否已投票
     */
    private Boolean hasVoted;

    private static final long serialVersionUID = 1L;
}
