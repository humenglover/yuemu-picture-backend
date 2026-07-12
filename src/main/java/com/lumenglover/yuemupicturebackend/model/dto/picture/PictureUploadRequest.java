package com.lumenglover.yuemupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传请求
 *
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 标签名称
     */
    private String tagName;

    /**
     * 分类名称
     */
    private String categoryName;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 缩略图URL（可选，用于AI上传时直接传递）
     */
    private String thumbnailUrl;

    /**
     * 图片大小（可选，用于AI上传时直接传递）
     */
    private Long picSize;

    /**
     * 图片宽度（可选，用于AI上传时直接传递）
     */
    private Integer picWidth;

    /**
     * 图片高度（可选，用于AI上传时直接传递）
     */
    private Integer picHeight;

    /**
     * 图片比例（可选，用于AI上传时直接传递）
     */
    private Double picScale;

    /**
     * 图片格式（可选，用于AI上传时直接传递）
     */
    private String picFormat;

    /**
     * 图片主色调（可选，用于AI上传时直接传递）
     */
    private String picColor;

    private static final long serialVersionUID = 1L;
}
