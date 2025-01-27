package com.lumenglover.yuemupicturebackend.model.dto.userfollows;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @TableName userfollows
 */
@Data
public class UserfollowsQueryRequest extends PageRequest implements Serializable {
    /**
     * 关注者的用户 ID
     */
    private Long followerId;
    /**
     * 被关注者的用户 ID
     */
    private Long followingId;
    /**
     * 搜索类型,0为关注，1为粉丝
     */
    private Integer searchType;

    private static final long serialVersionUID = 3L;
}
