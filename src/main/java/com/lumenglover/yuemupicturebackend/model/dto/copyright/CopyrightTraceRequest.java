package com.lumenglover.yuemupicturebackend.model.dto.copyright;

import lombok.Data;

import java.io.Serializable;

/**
 * 版权溯源请求
 */
@Data
public class CopyrightTraceRequest implements Serializable {
    
    /**
     * 版权溯源码
     */
    private String copyrightCode;

    private static final long serialVersionUID = 1L;
}
