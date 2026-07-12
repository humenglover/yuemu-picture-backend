package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.entity.AuthorRanking;
import com.lumenglover.yuemupicturebackend.model.vo.AuthorRankingVO;

import java.util.List;

/**
 * 作者榜单服务
 */
public interface AuthorRankingService extends IService<AuthorRanking> {

    /**
     * 获取作者榜单列表
     * @param rankingType 榜单类型：picture-图片作者榜, post-帖子作者榜
     * @param timeRange 时间范围：day-日榜, week-周榜, month-月榜, total-总榜
     * @param limit 返回数量限制
     * @return 作者榜单列表
     */
    List<AuthorRankingVO> getAuthorRankingList(String rankingType, String timeRange, Integer limit);

    /**
     * 计算并更新图片作者榜单
     * @param timeRange 时间范围
     */
    void calculatePictureAuthorRanking(String timeRange);

    /**
     * 计算并更新帖子作者榜单
     * @param timeRange 时间范围
     */
    void calculatePostAuthorRanking(String timeRange);

    /**
     * 计算所有榜单
     */
    void calculateAllRankings();
}
