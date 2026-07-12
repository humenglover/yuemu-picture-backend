package com.lumenglover.yuemupicturebackend.model.dto.picture;

import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 语义搜索请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class SearchPictureBySemanticRequest extends PageRequest implements Serializable {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 空间 id (可选)
     */
    private Long spaceId;

    /**
     * 用户 id (可选，用于过滤该用户的图片)
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}
