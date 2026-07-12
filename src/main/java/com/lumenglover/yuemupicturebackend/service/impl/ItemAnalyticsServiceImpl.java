package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.mapper.*;
import com.lumenglover.yuemupicturebackend.model.entity.*;
import com.lumenglover.yuemupicturebackend.model.vo.InteractionUserVO;
import com.lumenglover.yuemupicturebackend.model.vo.ItemAnalyticsVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.ItemAnalyticsService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ItemAnalyticsServiceImpl implements ItemAnalyticsService {

    @Resource
    private LikeRecordMapper likeRecordMapper;

    @Resource
    private ShareRecordMapper shareRecordMapper;

    @Resource
    private CommentsMapper commentsMapper;

    @Resource
    private ViewRecordMapper viewRecordMapper;

    @Resource
    private FavoriteRecordMapper favoriteRecordMapper;

    @Resource
    private UserMapper userMapper;

    @Override
    public ItemAnalyticsVO getPictureAnalytics(Long pictureId) {
        return getItemAnalytics(pictureId, 1);
    }

    @Override
    public ItemAnalyticsVO getPostAnalytics(Long postId) {
        return getItemAnalytics(postId, 2);
    }

    private ItemAnalyticsVO getItemAnalytics(Long targetId, Integer targetType) {
        ItemAnalyticsVO vo = new ItemAnalyticsVO();

        // 1. 获取概览数据
        vo.setOverview(getOverview(targetId, targetType));

        // 2. 获取趋势数据
        vo.setTrend(getTrend(targetId, targetType));

        // 3. 计算雷达图数据
        vo.setRadar(getRadar(vo.getOverview()));

        return vo;
    }

    private ItemAnalyticsVO.OverviewData getOverview(Long targetId, Integer targetType) {
        ItemAnalyticsVO.OverviewData overview = new ItemAnalyticsVO.OverviewData();

        // 总数
        overview.setLikes(countLikes(targetId, targetType, null));
        overview.setShares(countShares(targetId, targetType, null));
        overview.setComments(countComments(targetId, targetType, null));
        overview.setViews(countViews(targetId, targetType, null));
        overview.setFavorites(countFavorites(targetId, targetType, null));

        // 较昨日变化 (昨天的数量)
        Date yesterday = DateUtil.beginOfDay(DateUtil.yesterday());
        overview.setLikesChange(countLikes(targetId, targetType, yesterday));
        overview.setSharesChange(countShares(targetId, targetType, yesterday));
        overview.setCommentsChange(countComments(targetId, targetType, yesterday));
        overview.setViewsChange(countViews(targetId, targetType, yesterday));
        overview.setFavoritesChange(countFavorites(targetId, targetType, yesterday));

        return overview;
    }

    private ItemAnalyticsVO.TrendData getTrend(Long targetId, Integer targetType) {
        ItemAnalyticsVO.TrendData trend = new ItemAnalyticsVO.TrendData();
        List<String> dates = new ArrayList<>();
        List<Long> likes = new ArrayList<>();
        List<Long> shares = new ArrayList<>();
        List<Long> comments = new ArrayList<>();
        List<Long> views = new ArrayList<>();
        List<Long> favorites = new ArrayList<>();

        // 获取最近30天
        for (int i = 29; i >= 0; i--) {
            DateTime date = DateUtil.offsetDay(new Date(), -i);
            String dateStr = DateUtil.formatDate(date);
            dates.add(dateStr);

            Date start = DateUtil.beginOfDay(date);
            Date end = DateUtil.endOfDay(date);

            likes.add(countLikesInRange(targetId, targetType, start, end));
            shares.add(countSharesInRange(targetId, targetType, start, end));
            comments.add(countCommentsInRange(targetId, targetType, start, end));
            views.add(countViewsInRange(targetId, targetType, start, end));
            favorites.add(countFavoritesInRange(targetId, targetType, start, end));
        }

        trend.setDates(dates);
        trend.setLikes(likes);
        trend.setShares(shares);
        trend.setComments(comments);
        trend.setViews(views);
        trend.setFavorites(favorites);

        return trend;
    }

    private ItemAnalyticsVO.RadarData getRadar(ItemAnalyticsVO.OverviewData overview) {
        ItemAnalyticsVO.RadarData radar = new ItemAnalyticsVO.RadarData();
        List<ItemAnalyticsVO.RadarData.RadarItem> items = new ArrayList<>();

        long views = Math.max(overview.getViews(), 1L); // 防止除以0

        // 1. 互动度: (点赞+评论)/浏览
        double engagement = ((double)(overview.getLikes() + overview.getComments()) / views) * 100;
        items.add(createRadarItem("互动度", Math.min(engagement * 5, 100.0))); // 适当放大倍数

        // 2. 传播力: 分享/浏览
        double reach = ((double)overview.getShares() / views) * 100;
        items.add(createRadarItem("传播力", Math.min(reach * 20, 100.0)));

        // 3. 收藏价值: 收藏/浏览
        double value = ((double)overview.getFavorites() / views) * 100;
        items.add(createRadarItem("收藏价值", Math.min(value * 10, 100.0)));

        // 4. 反馈质量: 评论/浏览
        double feedback = ((double)overview.getComments() / views) * 100;
        items.add(createRadarItem("反馈质量", Math.min(feedback * 15, 100.0)));

        // 5. 点击热度: 浏览量级 (对数刻度)
        double popularity = Math.log10(views) * 20; // 10000次浏览约为 80分
        items.add(createRadarItem("点击热度", Math.min(popularity, 100.0)));

        radar.setItems(items);
        return radar;
    }

    private ItemAnalyticsVO.RadarData.RadarItem createRadarItem(String name, Double value) {
        ItemAnalyticsVO.RadarData.RadarItem item = new ItemAnalyticsVO.RadarData.RadarItem();
        item.setName(name);
        item.setValue(Math.round(value * 10) / 10.0);
        item.setMax(100.0);
        return item;
    }

    @Override
    public Page<InteractionUserVO> getInteractionList(Long targetId, Integer targetType, String type, long current, long size) {
        Page<InteractionUserVO> resultPage = new Page<>(current, size);
        List<InteractionUserVO> records = new ArrayList<>();
        long total = 0;

        switch (type.toLowerCase()) {
            case "like":
                Page<LikeRecord> likePage = likeRecordMapper.selectPage(new Page<>(current, size),
                        new QueryWrapper<LikeRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isLiked", 1).orderByDesc("lastLikeTime"));
                total = likePage.getTotal();
                records = likePage.getRecords().stream().map(r -> {
                    InteractionUserVO vo = new InteractionUserVO();
                    vo.setUser(UserVO.objToVo(userMapper.selectById(r.getUserId())));
                    vo.setInteractionTime(r.getLastLikeTime());
                    vo.setType("like");
                    return vo;
                }).collect(Collectors.toList());
                break;
            case "share":
                Page<ShareRecord> sharePage = shareRecordMapper.selectPage(new Page<>(current, size),
                        new QueryWrapper<ShareRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isShared", 1).orderByDesc("shareTime"));
                total = sharePage.getTotal();
                records = sharePage.getRecords().stream().map(r -> {
                    InteractionUserVO vo = new InteractionUserVO();
                    vo.setUser(UserVO.objToVo(userMapper.selectById(r.getUserId())));
                    vo.setInteractionTime(r.getShareTime());
                    vo.setType("share");
                    return vo;
                }).collect(Collectors.toList());
                break;
            case "comment":
                Page<Comments> commentPage = commentsMapper.selectPage(new Page<>(current, size),
                        new QueryWrapper<Comments>().eq("targetId", targetId).eq("targetType", targetType).eq("isDelete", 0).orderByDesc("createTime"));
                total = commentPage.getTotal();
                records = commentPage.getRecords().stream().map(r -> {
                    InteractionUserVO vo = new InteractionUserVO();
                    vo.setUser(UserVO.objToVo(userMapper.selectById(r.getUserId())));
                    vo.setInteractionTime(r.getCreateTime());
                    vo.setType("comment");
                    vo.setExtra(r.getContent());
                    return vo;
                }).collect(Collectors.toList());
                break;
            case "view":
                Page<ViewRecord> viewPage = viewRecordMapper.selectPage(new Page<>(current, size),
                        new QueryWrapper<ViewRecord>().eq("targetId", targetId).eq("targetType", targetType).orderByDesc("createTime"));
                total = viewPage.getTotal();
                records = viewPage.getRecords().stream().map(r -> {
                    InteractionUserVO vo = new InteractionUserVO();
                    vo.setUser(UserVO.objToVo(userMapper.selectById(r.getUserId())));
                    vo.setInteractionTime(r.getCreateTime());
                    vo.setType("view");
                    return vo;
                }).collect(Collectors.toList());
                break;
            case "favorite":
                Page<FavoriteRecord> favPage = favoriteRecordMapper.selectPage(new Page<>(current, size),
                        new QueryWrapper<FavoriteRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isFavorite", 1).orderByDesc("favoriteTime"));
                total = favPage.getTotal();
                records = favPage.getRecords().stream().map(r -> {
                    InteractionUserVO vo = new InteractionUserVO();
                    vo.setUser(UserVO.objToVo(userMapper.selectById(r.getUserId())));
                    vo.setInteractionTime(r.getFavoriteTime());
                    vo.setType("favorite");
                    return vo;
                }).collect(Collectors.toList());
                break;
        }

        resultPage.setRecords(records);
        resultPage.setTotal(total);
        return resultPage;
    }

    // Helper methods for counting
    private Long countLikes(Long targetId, Integer targetType, Date date) {
        QueryWrapper<LikeRecord> qw = new QueryWrapper<LikeRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isLiked", 1);
        if (date != null) qw.ge("lastLikeTime", date).le("lastLikeTime", DateUtil.endOfDay(date));
        return likeRecordMapper.selectCount(qw);
    }
    private Long countShares(Long targetId, Integer targetType, Date date) {
        QueryWrapper<ShareRecord> qw = new QueryWrapper<ShareRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isShared", 1);
        if (date != null) qw.ge("shareTime", date).le("shareTime", DateUtil.endOfDay(date));
        return shareRecordMapper.selectCount(qw);
    }
    private Long countComments(Long targetId, Integer targetType, Date date) {
        QueryWrapper<Comments> qw = new QueryWrapper<Comments>().eq("targetId", targetId).eq("targetType", targetType).eq("isDelete", 0);
        if (date != null) qw.ge("createTime", date).le("createTime", DateUtil.endOfDay(date));
        return commentsMapper.selectCount(qw);
    }
    private Long countViews(Long targetId, Integer targetType, Date date) {
        QueryWrapper<ViewRecord> qw = new QueryWrapper<ViewRecord>().eq("targetId", targetId).eq("targetType", targetType);
        if (date != null) qw.ge("createTime", date).le("createTime", DateUtil.endOfDay(date));
        return viewRecordMapper.selectCount(qw);
    }
    private Long countFavorites(Long targetId, Integer targetType, Date date) {
        QueryWrapper<FavoriteRecord> qw = new QueryWrapper<FavoriteRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isFavorite", 1);
        if (date != null) qw.ge("favoriteTime", date).le("favoriteTime", DateUtil.endOfDay(date));
        return favoriteRecordMapper.selectCount(qw);
    }

    private Long countLikesInRange(Long targetId, Integer targetType, Date start, Date end) {
        return likeRecordMapper.selectCount(new QueryWrapper<LikeRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isLiked", 1).ge("lastLikeTime", start).le("lastLikeTime", end));
    }
    private Long countSharesInRange(Long targetId, Integer targetType, Date start, Date end) {
        return shareRecordMapper.selectCount(new QueryWrapper<ShareRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isShared", 1).ge("shareTime", start).le("shareTime", end));
    }
    private Long countCommentsInRange(Long targetId, Integer targetType, Date start, Date end) {
        return commentsMapper.selectCount(new QueryWrapper<Comments>().eq("targetId", targetId).eq("targetType", targetType).eq("isDelete", 0).ge("createTime", start).le("createTime", end));
    }
    private Long countViewsInRange(Long targetId, Integer targetType, Date start, Date end) {
        return viewRecordMapper.selectCount(new QueryWrapper<ViewRecord>().eq("targetId", targetId).eq("targetType", targetType).ge("createTime", start).le("createTime", end));
    }
    private Long countFavoritesInRange(Long targetId, Integer targetType, Date start, Date end) {
        return favoriteRecordMapper.selectCount(new QueryWrapper<FavoriteRecord>().eq("targetId", targetId).eq("targetType", targetType).eq("isFavorite", 1).ge("favoriteTime", start).le("favoriteTime", end));
    }
}
