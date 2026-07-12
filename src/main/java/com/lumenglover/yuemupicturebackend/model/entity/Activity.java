package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.utils.VoUrlReplaceUtil;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;


/**
 * 活动表
 */
@TableName(value = "activity")
@Data
public class Activity implements Serializable {
    /**
     * 活动ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 发布用户ID
     */
    private Long userId;

    /**
     * 空间ID
     */
    private Long spaceId;

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
     * 浏览量
     */
    private Long viewCount;

    /**
     * 点赞数
     */
    private Long likeCount;

    /**
     * 评论数
     */
    private Long commentCount;

    /**
     * 状态 0-待审核 1-已发布 2-已拒绝
     */
    private Integer status;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    /**
     * 分享数
     */
    private Long shareCount;

    /**
     * 活动过期时间
     */
    private Date expireTime;

    /**
     * 是否过期 0-未过期 1-已过期
     */
    private Integer isExpired;

    /**
     * 创建用户信息
     */
    @TableField(exist = false)
    private UserVO user;



    /**
     * 是否已点赞 0-未点赞 1-已点赞
     */
    @TableField(exist = false)
    private Integer isLiked;

    /**
     * 是否已分享 0-未分享 1-已分享
     */
    @TableField(exist = false)
    private Integer isShared;

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
     * 提交数量
     */
    private Integer submissionCount;

    /**
     * 投票数量
     */
    private Integer voteCount;

    /**
     * 上传的图片是否需要审核：0-否 1-是
     */
    private Integer isNeedAudit;

    /**
     * 当前用户已投票数量
     */
    @TableField(exist = false)
    private Integer userVoteCount;

    /**
     * 当前用户剩余投票数量
     */
    @TableField(exist = false)
    private Integer remainingVotes;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    /**
     * 替换URL为自定义域名
     */
    public void replaceUrlWithCustomDomain() {
        this.coverUrl = VoUrlReplaceUtil.replaceUrl(this.coverUrl);
    }

    /**
     * 检查活动是否过期
     *
     * @return true if expired, false otherwise
     */
    public boolean isActivityExpired() {
        if (this.expireTime == null) {
            return false; // 如果没有设置过期时间，则认为不过期
        }
        return this.expireTime.before(new Date());
    }
}
