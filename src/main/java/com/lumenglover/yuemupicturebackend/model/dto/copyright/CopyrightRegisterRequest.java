package com.lumenglover.yuemupicturebackend.model.dto.copyright;

import lombok.Data;

import java.io.Serializable;

/**
 * 版权登记请求
 */
@Data
public class CopyrightRegisterRequest implements Serializable {
    
    /**
     * 图片ID
     */
    private Long pictureId;

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

    private static final long serialVersionUID = 1L;
}
