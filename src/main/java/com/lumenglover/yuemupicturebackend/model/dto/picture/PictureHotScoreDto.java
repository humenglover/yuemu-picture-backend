package com.lumenglover.yuemupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片热榜分数计算DTO
 * 仅包含计算热榜分数所需的关键字段，减少数据库查询数据量
 */
@Data
public class PictureHotScoreDto implements Serializable {
    /**
     * 图片ID
     */
    private Long id;

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
     * 分享数
     */
    private Long shareCount;

    /**
     * 创建时间（用于时间衰减计算）
     */
    private Date createTime;

    /**
     * 热榜分数（计算结果）
     */
    private Double hotScore;

    private static final long serialVersionUID = 1L;
}
