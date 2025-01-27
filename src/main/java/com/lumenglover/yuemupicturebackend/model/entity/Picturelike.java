package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 点赞表
 * @TableName picturelike
 */
@TableName(value ="picturelike")
@Data
public class Picturelike implements Serializable {
    /**
     * id
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 图片 ID
     */
    private Long pictureId;

    /**
     * 用户是否点赞（true 表示点赞，false 表示取消点赞）
     */
    private Integer isLiked;

    /**
     * 第一次点赞的时间
     */
    private Date firstLikeTime;

    /**
     * 最近一次点赞的时间
     */
    private Date lastLikeTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
