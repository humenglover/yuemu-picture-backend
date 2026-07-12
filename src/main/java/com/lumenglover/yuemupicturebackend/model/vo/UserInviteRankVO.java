package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;
import java.io.Serializable;

/**
 * 邀请排行榜视图对象
 */
@Data
public class UserInviteRankVO implements Serializable {
    private Long userId;
    private String userName;
    private String userAvatar;
    private Integer inviteCount;
    private static final long serialVersionUID = 1L;
}
