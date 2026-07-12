package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 创作者数据分析 VO
 */
@Data
public class CreatorAnalyticsVO implements Serializable {

    /**
     * 总览数据
     */
    private OverviewData overview;

    /**
     * 趋势数据（最近30天）
     */
    private TrendData trend;

    /**
     * 热门作品 Top 10
     */
    private List<TopWork> topWorks;

    /**
     * 分类分析
     */
    private List<CategoryStats> categoryStats;

    /**
     * 用户画像
     */
    private AudienceProfile audienceProfile;

    @Data
    public static class OverviewData implements Serializable {
        /**
         * 图片总数
         */
        private Long totalPictures;

        /**
         * 帖子总数
         */
        private Long totalPosts;

        /**
         * 总浏览量
         */
        private Long totalViews;

        /**
         * 总点赞数
         */
        private Long totalLikes;

        /**
         * 总收藏数
         */
        private Long totalFavorites;

        /**
         * 总评论数
         */
        private Long totalComments;

        /**
         * 粉丝数
         */
        private Long fansCount;

        /**
         * 较昨日浏览量变化
         */
        private Integer viewsChange;

        /**
         * 较昨日点赞变化
         */
        private Integer likesChange;

        /**
         * 较昨日粉丝变化
         */
        private Integer fansChange;
    }

    @Data
    public static class TrendData implements Serializable {
        /**
         * 日期列表（最近30天）
         */
        private List<String> dates;

        /**
         * 浏览量趋势
         */
        private List<Long> views;

        /**
         * 点赞趋势
         */
        private List<Long> likes;

        /**
         * 收藏趋势
         */
        private List<Long> favorites;

        /**
         * 粉丝增长趋势
         */
        private List<Long> fans;
    }

    @Data
    public static class TopWork implements Serializable {
        /**
         * 作品ID
         */
        private Long id;

        /**
         * 作品名称
         */
        private String name;

        /**
         * 作品类型（1-图片，2-帖子）
         */
        private Integer type;

        /**
         * 缩略图
         */
        private String thumbnail;

        /**
         * 浏览量
         */
        private Long views;

        /**
         * 点赞数
         */
        private Long likes;

        /**
         * 收藏数
         */
        private Long favorites;

        /**
         * 评论数
         */
        private Long comments;

        /**
         * 发布时间
         */
        private String createTime;
    }

    @Data
    public static class CategoryStats implements Serializable {
        /**
         * 分类名称
         */
        private String category;

        /**
         * 作品数量
         */
        private Long count;

        /**
         * 总浏览量
         */
        private Long views;

        /**
         * 平均浏览量
         */
        private Double avgViews;

        /**
         * 总点赞数
         */
        private Long likes;
    }

    @Data
    public static class AudienceProfile implements Serializable {
        /**
         * 地区分布 Top 10
         */
        private List<RegionData> regions;

        /**
         * 活跃时段分布（0-23小时）
         */
        private List<HourData> activeHours;
    }

    @Data
    public static class RegionData implements Serializable {
        /**
         * 地区名称
         */
        private String region;

        /**
         * 用户数量
         */
        private Long count;

        /**
         * 占比
         */
        private Double percentage;
    }

    @Data
    public static class HourData implements Serializable {
        /**
         * 小时（0-23）
         */
        private Integer hour;

        /**
         * 活跃度
         */
        private Long activity;
    }

    private static final long serialVersionUID = 1L;
}
