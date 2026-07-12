package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 版权信息视图对象
 */
@Data
public class CopyrightVO implements Serializable {

    /**
     * 主键ID
     */
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
     * 版权溯源码
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
     * 是否允许商用
     */
    private Integer allowCommercial;

    /**
     * 是否要求署名
     */
    private Integer requireAttribution;

    /**
     * 溯源查询次数
     */
    private Long traceCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 图片信息（可选）
     */
    private PictureVO picture;

    private static final long serialVersionUID = 1L;
}
