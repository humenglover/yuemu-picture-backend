package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 互动用户信息 VO
 */
@Data
public class InteractionUserVO implements Serializable {

    /**
     * 用户信息
     */
    private UserVO user;

    /**
     * 互动时间
     */
    private Date interactionTime;

    /**
     * 互动类型：like, share, comment, view, favorite
     */
    private String type;

    /**
     * 额外信息（如评论内容）
     */
    private String extra;

    private static final long serialVersionUID = 1L;
}
