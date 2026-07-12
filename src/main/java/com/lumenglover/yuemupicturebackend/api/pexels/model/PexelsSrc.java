package com.lumenglover.yuemupicturebackend.api.pexels.model;

import lombok.Data;

/**
 * Pexels 图片源
 */
@Data
public class PexelsSrc {
    
    /**
     * 原图
     */
    private String original;
    
    /**
     * 大图 2x
     */
    private String large2x;
    
    /**
     * 大图
     */
    private String large;
    
    /**
     * 中图
     */
    private String medium;
    
    /**
     * 小图
     */
    private String small;
    
    /**
     * 竖图
     */
    private String portrait;
    
    /**
     * 横图
     */
    private String landscape;
    
    /**
     * 缩略图
     */
    private String tiny;
}
