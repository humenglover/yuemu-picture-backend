package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @TableName comments
 */
@TableName(value ="comments")
@Data
public class Comments implements Serializable {
    /**
     *
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long commentId;

    /**
     *
     */
    private Long userId;

    /**
     *
     */
    private Long pictureId;

    /**
     *
     */
    private String content;

    /**
     *
     */
    private Date createTime;

    /**
     *父类
     */
    private Long parentCommentId;

    /**
     *
     */
    private Integer isDelete;

    /**
     *
     */
    private Long likeCount;

    /**
     *
     */
    private Long dislikeCount;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
