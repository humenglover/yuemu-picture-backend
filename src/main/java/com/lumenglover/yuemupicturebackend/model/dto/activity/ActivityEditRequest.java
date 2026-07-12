package com.lumenglover.yuemupicturebackend.model.dto.activity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 活动编辑请求
 */
@Data
public class ActivityEditRequest implements Serializable {

    /**
     * 活动ID
     */
    private Long id;

    /**
     * 活动标题
     */
    private String title;

    /**
     * 活动内容
     */
    private String content;

    /**
     * 封面图URL
     */
    private String coverUrl;

    /**
     * 活动过期时间
     */
    private Date expireTime;

    /**
     * 是否允许提交作品：0-不允许 1-允许
     */
    private Integer allowSubmission;

    /**
     * 作品提交开始时间
     */
    private Date submissionStartTime;

    /**
     * 作品提交结束时间
     */
    private Date submissionEndTime;

    /**
     * 每人最多提交作品数
     */
    private Integer maxSubmissionsPerUser;

    /**
     * 是否允许投票：0-不允许 1-允许
     */
    private Integer allowVote;

    /**
     * 投票开始时间
     */
    private Date voteStartTime;

    /**
     * 投票结束时间
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
     * 空间ID
     */
    private Long spaceId;

    /**
     * 上传的图片是否需要审核：0-否 1-是
     */
    private Integer isNeedAudit;

    private static final long serialVersionUID = 1L;
}
