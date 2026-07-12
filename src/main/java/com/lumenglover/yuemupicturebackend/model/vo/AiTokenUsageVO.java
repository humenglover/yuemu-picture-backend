package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * AI Token 使用情况展示视图
 */
@Data
public class AiTokenUsageVO implements Serializable {

    /**
     * 当前会员等级：0=普通，1=Pro，2=Plus
     */
    private Integer memberType;

    /**
     * 5小时额度上限
     */
    private Long limit5h;

    /**
     * 5小时内已使用额度
     */
    private Long used5h;

    /**
     * 本周额度上限
     */
    private Long limitWeek;

    /**
     * 本周已使用额度
     */
    private Long usedWeek;

    /**
     * 本周图片生成上限
     */
    private Long limitImageGenWeek;

    /**
     * 本周已使用图片生成次数
     */
    private Long usedImageGenWeek;

    /**
     * 本周以图搜图上限
     */
    private Long limitImageSearchWeek;

    /**
     * 本周已使用以图搜图次数
     */
    private Long usedImageSearchWeek;

    private static final long serialVersionUID = 1L;
}
