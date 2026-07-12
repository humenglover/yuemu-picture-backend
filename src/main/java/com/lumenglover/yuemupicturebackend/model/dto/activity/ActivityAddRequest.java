package com.lumenglover.yuemupicturebackend.model.dto.activity;

import lombok.Data;

import java.util.Date;

/**
 * 创建活动请求
 */
@Data
public class ActivityAddRequest {
    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 封面图片URL
     */
    private String coverUrl;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 活动过期时间
     */
    private Date expireTime;

    /**
     * 活动类型：0-通知 1-收集 2-投票
     */
    private Integer activityType;

    /**
     * 是否允许提交：0-否 1-是
     */
    private Integer allowSubmission;

    /**
     * 提交开始时间
     */
    private Date submissionStartTime;

    /**
     * 提交截止时间
     */
    private Date submissionEndTime;

    /**
     * 每人最多提交数量
     */
    private Integer maxSubmissionsPerUser;

    /**
     * 是否允许投票：0-否 1-是
     */
    private Integer allowVote;

    /**
     * 投票开始时间
     */
    private Date voteStartTime;

    /**
     * 投票截止时间
     */
    private Date voteEndTime;

    /**
     * 投票类型：0-单选 1-多选
     */
    private Integer voteType;

    /**
     * 每人最多投票数
     */
    private Integer maxVotesPerUser;

    /**
     * 上传的图片是否需要审核：0-否 1-是
     */
    private Integer isNeedAudit;
}
