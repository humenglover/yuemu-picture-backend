package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.mapper.AuthorRankingMapper;
import com.lumenglover.yuemupicturebackend.model.entity.AuthorRanking;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.enums.AuthorRankingTypeEnum;
import com.lumenglover.yuemupicturebackend.model.enums.TimeRangeEnum;
import com.lumenglover.yuemupicturebackend.model.vo.AuthorRankingVO;
import com.lumenglover.yuemupicturebackend.model.vo.FollowersAndFansVO;
import com.lumenglover.yuemupicturebackend.service.AuthorRankingService;
import com.lumenglover.yuemupicturebackend.service.UserFollowsService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作者榜单服务实现
 */
@Service
@Slf4j
public class AuthorRankingServiceImpl extends ServiceImpl<AuthorRankingMapper, AuthorRanking>
        implements AuthorRankingService {

    @Resource
    private UserService userService;

    @Resource
    private UserFollowsService userFollowsService;

    @Resource
    private com.lumenglover.yuemupicturebackend.service.PictureService pictureService;

    @Resource
    private com.lumenglover.yuemupicturebackend.service.PostService postService;

    @Resource
    private com.lumenglover.yuemupicturebackend.manager.RankingRedisManager rankingRedisManager;

    @Override
    public List<AuthorRankingVO> getAuthorRankingList(String rankingType, String timeRange, Integer limit) {
        // 参数校验
        if (rankingType == null || timeRange == null) {
            return new ArrayList<>();
        }

        // 默认返回100条
        if (limit == null || limit <= 0) {
            limit = 100;
        }
        limit = Math.min(limit, 100);

        // 1. 先尝试从 Redis 获取榜单
        List<AuthorRankingVO> cachedResult = getFromRedisCache(rankingType, timeRange, limit);
        if (cachedResult != null && !cachedResult.isEmpty()) {
            log.info("从 Redis 缓存获取榜单成功: rankingType={}, timeRange={}, size={}",
                    rankingType, timeRange, cachedResult.size());
            return cachedResult;
        }

        log.info("Redis 缓存未命中，从数据库查询榜单: rankingType={}, timeRange={}",
                rankingType, timeRange);

        // 2. Redis 未命中，从数据库查询
        QueryWrapper<AuthorRanking> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("rankingType", rankingType)
                .eq("timeRange", timeRange)
                .orderByDesc("rankingScore")
                .last("LIMIT " + limit);

        List<AuthorRanking> rankings = this.list(queryWrapper);

        // 转换为VO并填充用户信息和作品
        List<AuthorRankingVO> voList = new ArrayList<>();
        for (int i = 0; i < rankings.size(); i++) {
            AuthorRanking ranking = rankings.get(i);
            AuthorRankingVO vo = convertToVO(ranking);

            // 填充用户信息
            User user = userService.getById(ranking.getUserId());
            if (user != null) {
                vo.setUser(userService.getUserVO(user));
            }

            // 为所有作者查询最近作品
            try {
                List<?> recentWorks = getRecentWorks(ranking.getUserId(), rankingType);
                vo.setRecentWorks(recentWorks);
            } catch (Exception e) {
                log.error("查询作者最近作品失败，userId: {}", ranking.getUserId(), e);
                vo.setRecentWorks(new ArrayList<>());
            }

            voList.add(vo);
        }

        // 3. 将结果缓存到 Redis
        if (!voList.isEmpty()) {
            cacheToRedis(rankingType, timeRange, voList);
        }

        return voList;
    }

    /**
     * 从 Redis 缓存获取榜单
     */
    private List<AuthorRankingVO> getFromRedisCache(String rankingType, String timeRange, int limit) {
        try {
            // 检查榜单是否存在
            if (!rankingRedisManager.rankingExists(rankingType, timeRange)) {
                return null;
            }

            // 获取排名前N的用户ID
            List<Long> topUserIds = rankingRedisManager.getTopRanking(rankingType, timeRange, limit);
            if (topUserIds.isEmpty()) {
                return null;
            }

            // 批量获取详情（先尝试从缓存获取）
            List<AuthorRankingVO> result = new ArrayList<>();
            for (Long userId : topUserIds) {
                AuthorRankingVO vo = rankingRedisManager.getCachedRankingDetail(rankingType, timeRange, userId);
                if (vo != null) {
                    result.add(vo);
                }
            }

            // 如果缓存的详情数量不够，返回null让其从数据库重新加载
            if (result.size() < topUserIds.size() * 0.8) {  // 至少要有80%的数据
                log.warn("Redis 详情缓存不完整: 期望={}, 实际={}", topUserIds.size(), result.size());
                return null;
            }

            return result;

        } catch (Exception e) {
            log.error("从 Redis 获取榜单失败", e);
            return null;
        }
    }

    /**
     * 将榜单缓存到 Redis
     */
    private void cacheToRedis(String rankingType, String timeRange, List<AuthorRankingVO> voList) {
        try {
            // 1. 缓存排名和分数到 ZSET
            Map<Long, Double> userScores = new HashMap<>();
            for (AuthorRankingVO vo : voList) {
                userScores.put(vo.getUserId(), vo.getRankingScore());
            }
            rankingRedisManager.batchAddToRanking(rankingType, timeRange, userScores);

            // 2. 缓存详情数据
            rankingRedisManager.batchCacheRankingDetails(rankingType, timeRange, voList);

            log.info("榜单缓存到 Redis 成功: rankingType={}, timeRange={}, size={}",
                    rankingType, timeRange, voList.size());

        } catch (Exception e) {
            log.error("缓存榜单到 Redis 失败", e);
        }
    }

    /**
     * 获取作者最近作品
     */
    private List<?> getRecentWorks(Long userId, String rankingType) {
        log.info("开始查询作者最近作品，userId: {}, rankingType: {}", userId, rankingType);

        if (AuthorRankingTypeEnum.PICTURE.getValue().equals(rankingType)) {
            // 查询图片作品（返回简化的实体，前端只需要id、url、thumbnailUrl）
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.lumenglover.yuemupicturebackend.model.entity.Picture> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 4);
            QueryWrapper<com.lumenglover.yuemupicturebackend.model.entity.Picture> wrapper = new QueryWrapper<>();
            wrapper.eq("userId", userId)
                    .eq("reviewStatus", 1)    // 审核通过
                    .eq("isDelete", 0)        // 未删除
                    .isNull("spaceId")        // 公共空间（spaceId为null）
                    .orderByDesc("createTime")
                    .select("id", "url", "thumbnailUrl", "name", "introduction");

            log.info("图片查询SQL条件 - userId: {}, reviewStatus: 1, isDelete: 0, spaceId: null", userId);

            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.lumenglover.yuemupicturebackend.model.entity.Picture> resultPage =
                    pictureService.page(page, wrapper);

            log.info("查询图片作者最近作品完成，userId: {}, 查询到数量: {}", userId, resultPage.getRecords().size());
            return resultPage.getRecords();
        } else if (AuthorRankingTypeEnum.POST.getValue().equals(rankingType)) {
            // 查询帖子作品（返回简化的实体，前端只需要id、title、coverImage）
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.lumenglover.yuemupicturebackend.model.entity.Post> page =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 4);
            QueryWrapper<com.lumenglover.yuemupicturebackend.model.entity.Post> wrapper = new QueryWrapper<>();
            wrapper.eq("userId", userId)
                    .eq("status", 1)          // 审核通过
                    .eq("isDelete", 0)        // 未删除
                    .orderByDesc("createTime")
                    .select("id", "title", "coverUrl", "content");

            log.info("帖子查询SQL条件 - userId: {}, status: 1, isDelete: 0", userId);

            com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.lumenglover.yuemupicturebackend.model.entity.Post> resultPage =
                    postService.page(page, wrapper);

            log.info("查询帖子作者最近作品完成，userId: {}, 查询到数量: {}", userId, resultPage.getRecords().size());
            if (resultPage.getRecords().isEmpty()) {
                log.warn("帖子查询结果为空！userId: {}, 请检查数据库中是否有符合条件的数据", userId);
            }
            return resultPage.getRecords();
        }
        log.warn("未知的榜单类型: {}", rankingType);
        return new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculatePictureAuthorRanking(String timeRange) {
        log.info("开始计算图片作者榜单，时间范围: {}", timeRange);

        TimeRangeEnum rangeEnum = TimeRangeEnum.getEnumByValue(timeRange);
        if (rangeEnum == null) {
            log.error("无效的时间范围: {}", timeRange);
            return;
        }

        // 计算时间范围
        Date endTime = new Date();
        Date startTime = null;
        if (rangeEnum.getDays() > 0) {
            startTime = DateUtil.offsetDay(endTime, -rangeEnum.getDays());
        }

        // 统计图片作者数据
        List<Map<String, Object>> statsData = this.baseMapper.calculatePictureAuthorStats(startTime, endTime);

        if (statsData.isEmpty()) {
            log.info("图片作者榜单无数据，时间范围: {}", timeRange);
            return;
        }

        // 构建榜单数据
        List<AuthorRanking> rankings = buildRankings(
                statsData,
                AuthorRankingTypeEnum.PICTURE.getValue(),
                timeRange
        );

        // 批量保存到数据库
        if (!rankings.isEmpty()) {
            this.baseMapper.batchInsertOrUpdate(rankings);
            log.info("图片作者榜单计算完成，时间范围: {}，作者数: {}", timeRange, rankings.size());

            // 同步到 Redis
            syncRankingToRedis(AuthorRankingTypeEnum.PICTURE.getValue(), timeRange, rankings);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculatePostAuthorRanking(String timeRange) {
        log.info("开始计算帖子作者榜单，时间范围: {}", timeRange);

        TimeRangeEnum rangeEnum = TimeRangeEnum.getEnumByValue(timeRange);
        if (rangeEnum == null) {
            log.error("无效的时间范围: {}", timeRange);
            return;
        }

        // 计算时间范围
        Date endTime = new Date();
        Date startTime = null;
        if (rangeEnum.getDays() > 0) {
            startTime = DateUtil.offsetDay(endTime, -rangeEnum.getDays());
        }

        // 统计帖子作者数据
        List<Map<String, Object>> statsData = this.baseMapper.calculatePostAuthorStats(startTime, endTime);

        if (statsData.isEmpty()) {
            log.info("帖子作者榜单无数据，时间范围: {}", timeRange);
            return;
        }

        // 构建榜单数据
        List<AuthorRanking> rankings = buildRankings(
                statsData,
                AuthorRankingTypeEnum.POST.getValue(),
                timeRange
        );

        // 批量保存到数据库
        if (!rankings.isEmpty()) {
            this.baseMapper.batchInsertOrUpdate(rankings);
            log.info("帖子作者榜单计算完成，时间范围: {}，作者数: {}", timeRange, rankings.size());

            // 同步到 Redis
            syncRankingToRedis(AuthorRankingTypeEnum.POST.getValue(), timeRange, rankings);
        }
    }

    /**
     * 将榜单数据同步到 Redis
     */
    private void syncRankingToRedis(String rankingType, String timeRange, List<AuthorRanking> rankings) {
        try {
            // 先删除旧的榜单缓存
            rankingRedisManager.deleteRanking(rankingType, timeRange);

            // 批量添加到 Redis ZSET
            Map<Long, Double> userScores = new HashMap<>();
            for (AuthorRanking ranking : rankings) {
                userScores.put(ranking.getUserId(), ranking.getRankingScore());
            }
            rankingRedisManager.batchAddToRanking(rankingType, timeRange, userScores);

            log.info("榜单同步到 Redis 成功: rankingType={}, timeRange={}, size={}",
                    rankingType, timeRange, rankings.size());

        } catch (Exception e) {
            log.error("榜单同步到 Redis 失败: rankingType={}, timeRange={}",
                    rankingType, timeRange, e);
        }
    }

    @Override
    public void calculateAllRankings() {
        log.info("开始计算所有作者榜单");

        // 计算图片作者榜单
        for (TimeRangeEnum rangeEnum : TimeRangeEnum.values()) {
            try {
                calculatePictureAuthorRanking(rangeEnum.getValue());
            } catch (Exception e) {
                log.error("计算图片作者榜单失败，时间范围: {}", rangeEnum.getValue(), e);
            }
        }

        // 计算帖子作者榜单
        for (TimeRangeEnum rangeEnum : TimeRangeEnum.values()) {
            try {
                calculatePostAuthorRanking(rangeEnum.getValue());
            } catch (Exception e) {
                log.error("计算帖子作者榜单失败，时间范围: {}", rangeEnum.getValue(), e);
            }
        }

        log.info("所有作者榜单计算完成");
    }

    /**
     * 构建榜单数据
     */
    private List<AuthorRanking> buildRankings(
            List<Map<String, Object>> statsData,
            String rankingType,
            String timeRange
    ) {
        List<AuthorRanking> rankings = new ArrayList<>();

        for (Map<String, Object> data : statsData) {
            Long userId = ((Number) data.get("userId")).longValue();

            // 获取用户信息
            User user = userService.getById(userId);
            if (user == null || user.getIsDelete() == 1) {
                continue;
            }

            // 获取粉丝和关注数
            FollowersAndFansVO followData = userFollowsService.getFollowAndFansCount(userId);

            // 计算账号年龄
            int accountAgeDays = 0;
            if (user.getCreateTime() != null) {
                accountAgeDays = (int) DateUtil.betweenDay(user.getCreateTime(), new Date(), true);
            }

            // 构建榜单实体
            AuthorRanking ranking = new AuthorRanking();
            ranking.setUserId(userId);
            ranking.setRankingType(rankingType);
            ranking.setTimeRange(timeRange);

            // 统计数据
            ranking.setContentCount(getLongValue(data, "contentCount"));
            ranking.setTotalViewCount(getLongValue(data, "totalViewCount"));
            ranking.setTotalLikeCount(getLongValue(data, "totalLikeCount"));
            ranking.setTotalCommentCount(getLongValue(data, "totalCommentCount"));
            ranking.setTotalFavoriteCount(getLongValue(data, "totalFavoriteCount"));
            ranking.setTotalShareCount(getLongValue(data, "totalShareCount"));

            // 社交数据
            ranking.setFansCount(followData != null ? followData.getFansCount() : 0L);
            ranking.setFollowCount(followData != null ? followData.getFollowCount() : 0L);

            // 时间数据
            ranking.setAccountAgeDays(accountAgeDays);
            ranking.setActiveDays(getIntValue(data, "activeDays"));
            ranking.setLastPublishTime(convertToDate(data.get("lastPublishTime")));

            // 计算榜单分数
            double score = calculateRankingScore(ranking, rankingType);
            ranking.setRankingScore(score);

            rankings.add(ranking);
        }

        // 按分数排序并设置排名
        rankings.sort((a, b) -> Double.compare(b.getRankingScore(), a.getRankingScore()));
        for (int i = 0; i < rankings.size(); i++) {
            rankings.get(i).setRankingPosition(i + 1);
        }

        return rankings;
    }

    /**
     * 计算榜单分数
     * 图片作者和帖子作者使用不同的权重
     */
    private double calculateRankingScore(AuthorRanking ranking, String rankingType) {
        double score = 0.0;

        if (AuthorRankingTypeEnum.PICTURE.getValue().equals(rankingType)) {
            // 图片作者榜单权重：更注重视觉互动（浏览、收藏）
            score += ranking.getContentCount() * 10.0;           // 发布数量
            score += ranking.getTotalViewCount() * 0.5;          // 浏览量权重较高
            score += ranking.getTotalLikeCount() * 3.0;          // 点赞
            score += ranking.getTotalCommentCount() * 5.0;       // 评论
            score += ranking.getTotalFavoriteCount() * 8.0;      // 收藏权重高
            score += ranking.getTotalShareCount() * 6.0;         // 分享
            score += ranking.getFansCount() * 15.0;              // 粉丝数

            // 活跃度加成
            if (ranking.getActiveDays() != null && ranking.getActiveDays() > 0) {
                score += ranking.getActiveDays() * 2.0;
            }

        } else if (AuthorRankingTypeEnum.POST.getValue().equals(rankingType)) {
            // 帖子作者榜单权重：更注重讨论互动（评论、点赞）
            score += ranking.getContentCount() * 15.0;           // 发布数量权重更高
            score += ranking.getTotalViewCount() * 0.3;          // 浏览量权重较低
            score += ranking.getTotalLikeCount() * 5.0;          // 点赞权重高
            score += ranking.getTotalCommentCount() * 10.0;      // 评论权重最高
            score += ranking.getTotalFavoriteCount() * 6.0;      // 收藏
            score += ranking.getTotalShareCount() * 8.0;         // 分享权重高
            score += ranking.getFansCount() * 12.0;              // 粉丝数

            // 活跃度加成
            if (ranking.getActiveDays() != null && ranking.getActiveDays() > 0) {
                score += ranking.getActiveDays() * 3.0;
            }
        }

        // 账号年龄加成（老用户有轻微加成）
        if (ranking.getAccountAgeDays() != null && ranking.getAccountAgeDays() > 30) {
            score += Math.log(ranking.getAccountAgeDays()) * 5.0;
        }

        return score;
    }

    /**
     * 转换为VO
     */
    private AuthorRankingVO convertToVO(AuthorRanking ranking) {
        AuthorRankingVO vo = new AuthorRankingVO();
        vo.setUserId(ranking.getUserId());
        vo.setRankingType(ranking.getRankingType());
        vo.setTimeRange(ranking.getTimeRange());
        vo.setContentCount(ranking.getContentCount());
        vo.setTotalViewCount(ranking.getTotalViewCount());
        vo.setTotalLikeCount(ranking.getTotalLikeCount());
        vo.setTotalCommentCount(ranking.getTotalCommentCount());
        vo.setTotalFavoriteCount(ranking.getTotalFavoriteCount());
        vo.setTotalShareCount(ranking.getTotalShareCount());
        vo.setFansCount(ranking.getFansCount());
        vo.setFollowCount(ranking.getFollowCount());
        vo.setAccountAgeDays(ranking.getAccountAgeDays());
        vo.setActiveDays(ranking.getActiveDays());
        vo.setLastPublishTime(ranking.getLastPublishTime());
        vo.setRankingScore(ranking.getRankingScore());
        vo.setRankingPosition(ranking.getRankingPosition());
        vo.setUpdateTime(ranking.getUpdateTime());
        return vo;
    }

    /**
     * 从Map中获取Long值
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * 从Map中获取Integer值
     */
    private Integer getIntValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    /**
     * 转换为Date类型（处理LocalDateTime）
     */
    private Date convertToDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return (Date) value;
        }
        if (value instanceof LocalDateTime) {
            LocalDateTime localDateTime = (LocalDateTime) value;
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
