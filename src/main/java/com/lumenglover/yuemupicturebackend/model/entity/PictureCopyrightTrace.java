package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 版权溯源查询记录表
 * @TableName picture_copyright_trace
 */
@TableName(value = "picture_copyright_trace")
@Data
public class PictureCopyrightTrace implements Serializable {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 版权信息ID
     */
    private Long copyrightId;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 版权溯源码
     */
    private String copyrightCode;

    /**
     * 查询用户ID（未登录为NULL）
     */
    private Long traceUserId;

    /**
     * 查询IP地址
     */
    private String traceIp;

    /**
     * 查询时间
     */
    private Date traceTime;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
