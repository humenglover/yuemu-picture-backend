package com.lumenglover.yuemupicturebackend.model.dto.timealbum;

import lombok.Data;

@Data
public class UpdatePictureIntroductionRequest {
    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 相册ID
     */
    private Long albumId;

    /**
     * 新的描述
     */
    private String introduction;
}
