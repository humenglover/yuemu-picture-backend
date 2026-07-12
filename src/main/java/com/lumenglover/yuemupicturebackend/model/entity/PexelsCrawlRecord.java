package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Pexels 抓取记录实体
 */
@TableName(value = "pexels_crawl_record")
@Data
public class PexelsCrawlRecord implements Serializable {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Pexels图片ID
     */
    private Long pexelsPhotoId;

    /**
     * Pexels原图链接
     */
    private String pexelsUrl;

    /**
     * 摄影师名称
     */
    private String photographer;

    /**
     * 摄影师主页
     */
    private String photographerUrl;

    /**
     * 摄影师ID
     */
    private Long photographerId;

    /**
     * 搜索关键词
     */
    private String queryKeyword;

    /**
     * 关联的本地分类ID
     */
    private Long categoryId;

    /**
     * 页码
     */
    private Integer pageNumber;

    /**
     * 抓取时间
     */
    private Date crawlTime;

    /**
     * 上传状态: 0-待上传, 1-已上传, 2-失败
     */
    private Integer uploadStatus;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 本地图片ID
     */
    private Long pictureId;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
