package com.lumenglover.yuemupicturebackend.model.dto.picture;

import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 以图搜图请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SearchPictureByPictureRequest extends PageRequest implements Serializable {

    /**
     * 图片 id（从已有图片发起搜图时必填）
     */
    private Long pictureId;

    /**
     * 图片 URL（用户外部上传或拍照搜图时必填，优先于 pictureId）
     */
    private String imageUrl;

    /**
     * 空间 id (可选)
     */
    private Long spaceId;

    private static final long serialVersionUID = 1L;
}
