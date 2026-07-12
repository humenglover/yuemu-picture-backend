package com.lumenglover.yuemupicturebackend.api.pexels.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Pexels 搜索响应
 */
@Data
public class PexelsSearchResponse {
    
    /**
     * 总结果数
     */
    @JsonProperty("total_results")
    private Integer totalResults;
    
    /**
     * 当前页码
     */
    private Integer page;
    
    /**
     * 每页数量
     */
    @JsonProperty("per_page")
    private Integer perPage;
    
    /**
     * 图片列表
     */
    private List<PexelsPhoto> photos;
    
    /**
     * 下一页URL
     */
    @JsonProperty("next_page")
    private String nextPage;
    
    /**
     * 上一页URL
     */
    @JsonProperty("prev_page")
    private String prevPage;
}
