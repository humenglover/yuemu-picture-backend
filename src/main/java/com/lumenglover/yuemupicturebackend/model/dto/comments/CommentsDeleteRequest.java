package com.lumenglover.yuemupicturebackend.model.dto.comments;

import lombok.Data;

import java.io.Serializable;

/**
 *
 * @TableName comments
 */
@Data
public class CommentsDeleteRequest implements Serializable {
    /**
     *  评论id
     */
    private Long commentId;

    /**
     *  用户id
     */
    private Long userId;

    /**
     * 图片id
     */
    private Long pictureId;

}
