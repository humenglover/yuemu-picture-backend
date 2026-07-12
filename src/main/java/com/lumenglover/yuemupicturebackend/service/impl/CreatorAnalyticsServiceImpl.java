package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.mapper.*;
import com.lumenglover.yuemupicturebackend.model.entity.*;
import com.lumenglover.yuemupicturebackend.model.vo.CreatorAnalyticsVO;
import com.lumenglover.yuemupicturebackend.service.CreatorAnalyticsService;
import com.lumenglover.yuemupicturebackend.service.UserFollowsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 创作者数据分析服务实现
 */
@Service
@Slf4j
public class CreatorAnalyticsServiceImpl implements CreatorAnalyticsService {

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private ViewRecordMapper viewRecordMapper;

    @Resource
    private LikeRecordMapper likeRecordMapper;

    @Resource
    private FavoriteRecordMapper favoriteRecordMapper;

    @Resource
    private CommentsMapper commentsMapper;

    @Resource
    private UserFollowsService userFollowsService;

    @Resource
    private UserMapper userMapper;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    @Override
    public CreatorAnalyticsVO getCreatorAnalytics(Long userId) {
        CreatorAnalyticsVO vo = new CreatorAnalyticsVO();

        // 1. 总览数据
        vo.setOverview(buildOverviewData(userId));

        // 2. 趋势数据
        vo.setTrend(buildTrendData(userId));

        // 3. 热门作品
        vo.setTopWorks(buildTopWorks(userId));

        // 4. 分类统计
        vo.setCategoryStats(buildCategoryStats(userId));

        // 5. 用户画像
        vo.setAudienceProfile(buildAudienceProfile(userId));

        return vo;
    }

    /**
     * 构建总览数据
     */
    private CreatorAnalyticsVO.OverviewData buildOverviewData(Long userId) {
        CreatorAnalyticsVO.OverviewData overview = new CreatorAnalyticsVO.OverviewData();

        // 图片总数
        QueryWrapper<Picture> pictureWrapper = new QueryWrapper<>();
        pictureWrapper.eq("userId", userId).eq("reviewStatus", 1);
        overview.setTotalPictures(pictureMapper.selectCount(pictureWrapper));

        // 帖子总数
        QueryWrapper<Post> postWrapper = new QueryWrapper<>();
        postWrapper.eq("userId", userId).eq("status", 1);
        overview.setTotalPosts(postMapper.selectCount(postWrapper));

        // 总浏览量（图片 + 帖子）
        QueryWrapper<Picture> pictureViewWrapper = new QueryWrapper<>();
        pictureViewWrapper.eq("userId", userId).eq("reviewStatus", 1);
        List<Picture> pictures = pictureMapper.selectList(pictureViewWrapper);
        long pictureViews = pictures.stream()
                .filter(p -> p.getViewCount() != null)
                .mapToLong(Picture::getViewCount)
                .sum();

        QueryWrapper<Post> postViewWrapper = new QueryWrapper<>();
        postViewWrapper.eq("userId", userId).eq("status", 1);
        List<Post> posts = postMapper.selectList(postViewWrapper);
        long postViews = posts.stream()
                .filter(p -> p.getViewCount() != null)
                .mapToLong(Post::getViewCount)
                .sum();

        overview.setTotalViews(pictureViews + postViews);

        // 总点赞数
        long pictureLikes = pictures.stream()
                .filter(p -> p.getLikeCount() != null)
                .mapToLong(Picture::getLikeCount)
                .sum();
        long postLikes = posts.stream()
                .filter(p -> p.getLikeCount() != null)
                .mapToLong(Post::getLikeCount)
                .sum();
        overview.setTotalLikes(pictureLikes + postLikes);

        // 总收藏数
        long pictureFavorites = pictures.stream()
                .filter(p -> p.getFavoriteCount() != null)
                .mapToLong(Picture::getFavoriteCount)
                .sum();
        long postFavorites = posts.stream()
                .filter(p -> p.getFavoriteCount() != null)
                .mapToLong(Post::getFavoriteCount)
                .sum();
        overview.setTotalFavorites(pictureFavorites + postFavorites);

        // 总评论数
        long pictureComments = pictures.stream()
                .filter(p -> p.getCommentCount() != null)
                .mapToLong(Picture::getCommentCount)
                .sum();
        long postComments = posts.stream()
                .filter(p -> p.getCommentCount() != null)
                .mapToLong(Post::getCommentCount)
                .sum();
        overview.setTotalComments(pictureComments + postComments);

        // 粉丝数
        overview.setFansCount(userFollowsService.getFollowAndFansCount(userId).getFansCount());

        // 计算变化（较昨日）- 简化处理，设置为0
        overview.setViewsChange(0);
        overview.setLikesChange(0);
        overview.setFansChange(0);

        return overview;
    }

    /**
     * 构建趋势数据（最近30天）- 显示每日新增
     */
    private CreatorAnalyticsVO.TrendData buildTrendData(Long userId) {
        CreatorAnalyticsVO.TrendData trend = new CreatorAnalyticsVO.TrendData();

        List<String> dates = new ArrayList<>();
        List<Long> views = new ArrayList<>();
        List<Long> likes = new ArrayList<>();
        List<Long> favorites = new ArrayList<>();
        List<Long> fans = new ArrayList<>();

        LocalDate today = LocalDate.now();

        // 获取用户的所有图片和帖子
        QueryWrapper<Picture> pictureWrapper = new QueryWrapper<>();
        pictureWrapper.eq("userId", userId).eq("reviewStatus", 1);
        List<Picture> pictures = pictureMapper.selectList(pictureWrapper);

        QueryWrapper<Post> postWrapper = new QueryWrapper<>();
        postWrapper.eq("userId", userId).eq("status", 1);
        List<Post> posts = postMapper.selectList(postWrapper);

        // 获取当前粉丝数
        long currentFans = userFollowsService.getFollowAndFansCount(userId).getFansCount();

        // 按日期统计每日新增作品
        for (int i = 29; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            dates.add(date.format(DATE_FORMATTER));

            // 统计该日期创建的作品数量和初始数据
            long dayNewPictures = pictures.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .count();

            long dayNewPosts = posts.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .count();

            // 该日期创建的作品的当前浏览量（作为该日的数据）
            long dayViews = pictures.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .filter(p -> p.getViewCount() != null)
                    .mapToLong(Picture::getViewCount)
                    .sum()
                    + posts.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .filter(p -> p.getViewCount() != null)
                    .mapToLong(Post::getViewCount)
                    .sum();

            long dayLikes = pictures.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .filter(p -> p.getLikeCount() != null)
                    .mapToLong(Picture::getLikeCount)
                    .sum()
                    + posts.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .filter(p -> p.getLikeCount() != null)
                    .mapToLong(Post::getLikeCount)
                    .sum();

            long dayFavorites = pictures.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .filter(p -> p.getFavoriteCount() != null)
                    .mapToLong(Picture::getFavoriteCount)
                    .sum()
                    + posts.stream()
                    .filter(p -> p.getCreateTime() != null)
                    .filter(p -> {
                        LocalDate createDate = p.getCreateTime().toInstant()
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
                        return createDate.equals(date);
                    })
                    .filter(p -> p.getFavoriteCount() != null)
                    .mapToLong(Post::getFavoriteCount)
                    .sum();

            views.add(dayViews);
            likes.add(dayLikes);
            favorites.add(dayFavorites);
            fans.add(currentFans); // 粉丝数暂时使用当前值
        }

        trend.setDates(dates);
        trend.setViews(views);
        trend.setLikes(likes);
        trend.setFavorites(favorites);
        trend.setFans(fans);

        return trend;
    }

    /**
     * 构建热门作品 Top 10
     */
    private List<CreatorAnalyticsVO.TopWork> buildTopWorks(Long userId) {
        List<CreatorAnalyticsVO.TopWork> topWorks = new ArrayList<>();

        // 1. 获取用户的图片
        QueryWrapper<Picture> pictureWrapper = new QueryWrapper<>();
        pictureWrapper.eq("userId", userId)
                .eq("reviewStatus", 1)
                .orderByDesc("viewCount")
                .last("LIMIT 10");
        List<Picture> pictures = pictureMapper.selectList(pictureWrapper);

        for (Picture picture : pictures) {
            CreatorAnalyticsVO.TopWork work = new CreatorAnalyticsVO.TopWork();
            work.setId(picture.getId());
            work.setName(picture.getName());
            work.setType(1); // 图片
            work.setThumbnail(picture.getThumbnailUrl());
            work.setViews(picture.getViewCount());
            work.setLikes(picture.getLikeCount());
            work.setFavorites(picture.getFavoriteCount());
            work.setComments(picture.getCommentCount());
            work.setCreateTime(picture.getCreateTime() != null ?
                    new java.text.SimpleDateFormat("yyyy-MM-dd").format(picture.getCreateTime()) : "");
            topWorks.add(work);
        }

        // 2. 获取用户的帖子
        QueryWrapper<Post> postWrapper = new QueryWrapper<>();
        postWrapper.eq("userId", userId)
                .eq("status", 1)
                .orderByDesc("viewCount")
                .last("LIMIT 10");
        List<Post> posts = postMapper.selectList(postWrapper);

        for (Post post : posts) {
            CreatorAnalyticsVO.TopWork work = new CreatorAnalyticsVO.TopWork();
            work.setId(post.getId());
            work.setName(post.getTitle());
            work.setType(2); // 帖子
            work.setThumbnail(post.getCoverUrl());
            work.setViews(post.getViewCount());
            work.setLikes(post.getLikeCount());
            work.setFavorites(post.getFavoriteCount());
            work.setComments(post.getCommentCount());
            work.setCreateTime(post.getCreateTime() != null ?
                    new java.text.SimpleDateFormat("yyyy-MM-dd").format(post.getCreateTime()) : "");
            topWorks.add(work);
        }

        // 3. 排序并取前10
        return topWorks.stream()
                .sorted((a, b) -> {
                    long viewsA = a.getViews() != null ? a.getViews() : 0;
                    long viewsB = b.getViews() != null ? b.getViews() : 0;
                    return Long.compare(viewsB, viewsA);
                })
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * 构建分类统计
     */
    private List<CreatorAnalyticsVO.CategoryStats> buildCategoryStats(Long userId) {
        Map<String, CreatorAnalyticsVO.CategoryStats> statsMap = new HashMap<>();

        // 1. 统计图片分类
        QueryWrapper<Picture> pictureWrapper = new QueryWrapper<>();
        pictureWrapper.eq("userId", userId).eq("reviewStatus", 1);
        List<Picture> pictures = pictureMapper.selectList(pictureWrapper);

        for (Picture p : pictures) {
            String category = p.getCategory() != null ? p.getCategory() : "未分类";
            CreatorAnalyticsVO.CategoryStats stats = statsMap.getOrDefault(category, new CreatorAnalyticsVO.CategoryStats());
            stats.setCategory(category);
            stats.setCount((stats.getCount() != null ? stats.getCount() : 0) + 1);
            stats.setViews((stats.getViews() != null ? stats.getViews() : 0) + (p.getViewCount() != null ? p.getViewCount() : 0));
            stats.setLikes((stats.getLikes() != null ? stats.getLikes() : 0) + (p.getLikeCount() != null ? p.getLikeCount() : 0));
            statsMap.put(category, stats);
        }

        // 2. 统计帖子分类
        QueryWrapper<Post> postWrapper = new QueryWrapper<>();
        postWrapper.eq("userId", userId).eq("status", 1);
        List<Post> posts = postMapper.selectList(postWrapper);

        for (Post p : posts) {
            String category = p.getCategory() != null ? p.getCategory() : "未分类";
            CreatorAnalyticsVO.CategoryStats stats = statsMap.getOrDefault(category, new CreatorAnalyticsVO.CategoryStats());
            stats.setCategory(category);
            stats.setCount((stats.getCount() != null ? stats.getCount() : 0) + 1);
            stats.setViews((stats.getViews() != null ? stats.getViews() : 0) + (p.getViewCount() != null ? p.getViewCount() : 0));
            stats.setLikes((stats.getLikes() != null ? stats.getLikes() : 0) + (p.getLikeCount() != null ? p.getLikeCount() : 0));
            statsMap.put(category, stats);
        }

        List<CreatorAnalyticsVO.CategoryStats> categoryStats = new ArrayList<>(statsMap.values());
        for (CreatorAnalyticsVO.CategoryStats stats : categoryStats) {
            stats.setAvgViews(stats.getCount() > 0 ? (double) stats.getViews() / stats.getCount() : 0.0);
        }

        // 按浏览量排序
        categoryStats.sort((a, b) -> b.getViews().compareTo(a.getViews()));

        return categoryStats;
    }

    /**
     * 构建用户画像
     */
    private CreatorAnalyticsVO.AudienceProfile buildAudienceProfile(Long userId) {
        CreatorAnalyticsVO.AudienceProfile profile = new CreatorAnalyticsVO.AudienceProfile();

        // 地区分布（基于粉丝用户）
        List<CreatorAnalyticsVO.RegionData> regions = new ArrayList<>();

        try {
            // 获取粉丝ID列表（followingId是被关注者，followerId是粉丝）
            QueryWrapper<Userfollows> followWrapper = new QueryWrapper<>();
            followWrapper.eq("followingId", userId).eq("followStatus", 1).select("followerId");
            List<Userfollows> follows = userFollowsService.list(followWrapper);

            if (CollUtil.isNotEmpty(follows)) {
                List<Long> fanIds = follows.stream()
                        .map(Userfollows::getFollowerId)
                        .filter(id -> id != null)
                        .distinct()
                        .collect(Collectors.toList());

                // 获取这些用户的地区信息
                if (CollUtil.isNotEmpty(fanIds)) {
                    QueryWrapper<User> userWrapper = new QueryWrapper<>();
                    userWrapper.in("id", fanIds).select("region");
                    List<User> users = userMapper.selectList(userWrapper);

                    if (CollUtil.isNotEmpty(users)) {
                        Map<String, Long> regionCount = users.stream()
                                .filter(u -> u != null && u.getRegion() != null && !u.getRegion().isEmpty())
                                .collect(Collectors.groupingBy(User::getRegion, Collectors.counting()));

                        long total = regionCount.values().stream().mapToLong(Long::longValue).sum();

                        if (total > 0) {
                            regions = regionCount.entrySet().stream()
                                    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                                    .limit(10)
                                    .map(entry -> {
                                        CreatorAnalyticsVO.RegionData data = new CreatorAnalyticsVO.RegionData();
                                        data.setRegion(entry.getKey());
                                        data.setCount(entry.getValue());
                                        data.setPercentage((double) entry.getValue() / total * 100);
                                        return data;
                                    })
                                    .collect(Collectors.toList());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取粉丝地区分布失败", e);
            // 出错时返回空列表
        }

        profile.setRegions(regions);

        // 活跃时段分布（简化：使用模拟数据）
        List<CreatorAnalyticsVO.HourData> activeHours = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            CreatorAnalyticsVO.HourData hourData = new CreatorAnalyticsVO.HourData();
            hourData.setHour(i);
            // 模拟活跃度：早上8-10点、中午12-14点、晚上19-22点较活跃
            long activity = 0L;
            if ((i >= 8 && i <= 10) || (i >= 12 && i <= 14) || (i >= 19 && i <= 22)) {
                activity = (long) (Math.random() * 50 + 30);
            } else {
                activity = (long) (Math.random() * 20 + 5);
            }
            hourData.setActivity(activity);
            activeHours.add(hourData);
        }

        profile.setActiveHours(activeHours);

        return profile;
    }
}
