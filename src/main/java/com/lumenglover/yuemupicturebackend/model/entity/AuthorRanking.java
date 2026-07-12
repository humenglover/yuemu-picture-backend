package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 作者榜单实体
 * @TableName author_ranking
 */
@TableName(value = "author_ranking")
@Data
public class AuthorRanking implements Serializable {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 榜单类型：picture-图片作者榜, post-帖子作者榜
     */
    private String rankingType;

    /**
     * 时间范围：day-日榜, week-周榜, month-月榜, total-总榜
     */
    private String timeRange;

    /**
     * 发布内容数量
     */
    private Long contentCount;

    /**
     * 总浏览量
     */
    private Long totalViewCount;

    /**
     * 总点赞数
     */
    private Long totalLikeCount;

    /**
     * 总评论数
     */
    private Long totalCommentCount;

    /**
     * 总收藏数
     */
    private Long totalFavoriteCount;

    /**
     * 总分享数
     */
    private Long totalShareCount;

    /**
     * 粉丝数
     */
    private Long fansCount;

    /**
     * 关注数
     */
    private Long followCount;

    /**
     * 账号年龄（天数）
     */
    private Integer accountAgeDays;

    /**
     * 活跃天数
     */
    private Integer activeDays;

    /**
     * 最后发布时间
     */
    private Date lastPublishTime;

    /**
     * 榜单综合分数
     */
    private Double rankingScore;

    /**
     * 榜单排名位置
     */
    private Integer rankingPosition;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
