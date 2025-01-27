package com.lumenglover.yuemupicturebackend.model.dto.comments;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @TableName comments
 */
@Data
public class CommentsAddRequest implements Serializable {

    /**
     *  用户id
     */
    private Long userId;

    /**
     *评论id
     */
    private Long pictureId;

    /**
     *内容
     */
    private String content;

    /**
     *父类
     */
    private Long parentCommentId;
}
