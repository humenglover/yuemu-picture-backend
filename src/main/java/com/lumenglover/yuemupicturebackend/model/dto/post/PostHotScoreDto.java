package com.lumenglover.yuemupicturebackend.model.dto.post;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 帖子热点分数 DTO
 */
@Data
public class PostHotScoreDto implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 浏览量
     */
    private Long viewCount;

    /**
     * 点赞量
     */
    private Long likeCount;

    /**
     * 评论量
     */
    private Long commentCount;

    /**
     * 分享量
     */
    private Long shareCount;

    /**
     * 创建时间（用于时间衰减计算）
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
