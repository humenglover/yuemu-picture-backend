package com.lumenglover.yuemupicturebackend.model.dto.comments;


import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 评论查询请求
 */
@Data
public class CommentsQueryRequest extends PageRequest implements Serializable {

    /**
     * 图片ID
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
