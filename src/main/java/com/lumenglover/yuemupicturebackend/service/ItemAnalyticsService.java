package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.model.vo.InteractionUserVO;
import com.lumenglover.yuemupicturebackend.model.vo.ItemAnalyticsVO;

/**
 * 内容分析服务
 */
public interface ItemAnalyticsService {

    /**
     * 获取图片分析数据
     */
    ItemAnalyticsVO getPictureAnalytics(Long pictureId);

    /**
     * 获取帖子分析数据
     */
    ItemAnalyticsVO getPostAnalytics(Long postId);

    /**
     * 获取互动列表
     * @param targetId 目标ID
     * @param targetType 1-图片 2-帖子
     * @param type 互动类型: like, share, comment, view, favorite
     * @param current 当前页
     * @param size 每页大小
     */
    Page<InteractionUserVO> getInteractionList(Long targetId, Integer targetType, String type, long current, long size);
}
