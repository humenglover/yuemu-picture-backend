package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片版权信息表（简化版）
 * @TableName picture_copyright
 */
@TableName(value = "picture_copyright")
@Data
public class PictureCopyright implements Serializable {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 版权所有者用户ID
     */
    private Long userId;

    /**
     * 版权溯源码（唯一标识）
     */
    private String copyrightCode;

    /**
     * 版权所有者姓名
     */
    private String copyrightOwner;

    /**
     * 版权说明
     */
    private String copyrightDesc;

    /**
     * 是否允许商用：0-禁止 1-允许
     */
    private Integer allowCommercial;

    /**
     * 是否要求署名：0-不要求 1-要求
     */
    private Integer requireAttribution;

    /**
     * 溯源查询次数
     */
    private Long traceCount;

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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
