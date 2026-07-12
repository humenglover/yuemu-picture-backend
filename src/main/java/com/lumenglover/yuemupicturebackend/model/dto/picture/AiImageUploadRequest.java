package com.lumenglover.yuemupicturebackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 图片上传请求
 */
@Data
public class AiImageUploadRequest implements Serializable {

    /**
     * Base64 编码的图片数据（支持 data:image/png;base64,xxx 格式）
     */
    private String imageData;

    /**
     * 图片名称（可选）
     */
    private String picName;

    /**
     * 图片来源说明（可选，如：AI生成、色板提取等）
     */
    private String source;

    private static final long serialVersionUID = 1L;
}
