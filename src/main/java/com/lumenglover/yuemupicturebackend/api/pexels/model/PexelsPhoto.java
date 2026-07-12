package com.lumenglover.yuemupicturebackend.api.pexels.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Pexels 图片模型
 */
@Data
public class PexelsPhoto {
    
    /**
     * 图片ID
     */
    private Long id;
    
    /**
     * 图片宽度
     */
    private Integer width;
    
    /**
     * 图片高度
     */
    private Integer height;
    
    /**
     * 图片URL
     */
    private String url;
    
    /**
     * 摄影师名称
     */
    private String photographer;
    
    /**
     * 摄影师主页
     */
    @JsonProperty("photographer_url")
    private String photographerUrl;
    
    /**
     * 摄影师ID
     */
    @JsonProperty("photographer_id")
    private Long photographerId;
    
    /**
     * 平均颜色
     */
    @JsonProperty("avg_color")
    private String avgColor;
    
    /**
     * 图片源
     */
    private PexelsSrc src;
    
    /**
     * 是否喜欢
     */
    private Boolean liked;
    
    /**
     * 替代文本
     */
    private String alt;
}
