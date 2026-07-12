package com.lumenglover.yuemupicturebackend.service;

import com.lumenglover.yuemupicturebackend.model.vo.CreatorAnalyticsVO;

/**
 * 创作者数据分析服务
 */
public interface CreatorAnalyticsService {

    /**
     * 获取创作者数据分析
     *
     * @param userId 用户ID
     * @return 数据分析结果
     */
    CreatorAnalyticsVO getCreatorAnalytics(Long userId);
}
