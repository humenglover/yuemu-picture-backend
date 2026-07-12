package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 单个内容（图片/帖子）分析 VO
 */
@Data
public class ItemAnalyticsVO implements Serializable {

    /**
     * 概览数据
     */
    private OverviewData overview;

    /**
     * 趋势数据（最近30天）
     */
    private TrendData trend;

    /**
     * 雷达图数据
     */
    private RadarData radar;

    @Data
    public static class OverviewData implements Serializable {
        private Long views;
        private Long likes;
        private Long shares;
        private Long comments;
        private Long favorites;

        // 较昨日增量
        private Long viewsChange;
        private Long likesChange;
        private Long sharesChange;
        private Long commentsChange;
        private Long favoritesChange;
    }

    @Data
    public static class TrendData implements Serializable {
        private List<String> dates;
        private List<Long> views;
        private List<Long> likes;
        private List<Long> shares;
        private List<Long> comments;
        private List<Long> favorites;
    }

    @Data
    public static class RadarData implements Serializable {
        private List<RadarItem> items;

        @Data
        public static class RadarItem implements Serializable {
            private String name; // 维度名称
            private Double value; // 分值 (0-100)
            private Double max; // 最大值 (通常是 100)
        }
    }

    private static final long serialVersionUID = 1L;
}
