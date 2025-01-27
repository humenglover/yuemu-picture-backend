package com.lumenglover.yuemupicturebackend.service;

import com.lumenglover.yuemupicturebackend.model.dto.search.SearchRequest;
import org.springframework.data.domain.Page;

public interface SearchService {
    /**
     * 统一搜索接口
     * @param searchRequest
     * @return
     */
    Page<?> doSearch(SearchRequest searchRequest);
} 