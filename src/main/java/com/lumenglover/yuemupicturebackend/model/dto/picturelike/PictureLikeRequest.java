package com.lumenglover.yuemupicturebackend.model.dto.picturelike;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 点赞请求
 */
@Data
public class PictureLikeRequest implements Serializable {

    /**
     * 图片 ID
     */
    private Long pictureId;

    /**
     * 用户是否点赞（true 表示点赞，false 表示取消点赞）
     */
    private Integer isLiked;
    private static final long serialVersionUID = 1L;
}
