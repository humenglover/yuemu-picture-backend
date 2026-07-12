package com.lumenglover.yuemupicturebackend.model.dto.timealbum;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
public class TimeAlbumHeartWallRequest {
    /**
     * 相册ID
     */
    private Long albumId;

    /**
     * 图片文件列表
     */
    private List<MultipartFile> files;

    /**
     * 图片描述（简介）
     */
    private String introduction;

    /**
     * 是否覆盖原有的爱心墙图片
     */
    private Boolean override;
}
