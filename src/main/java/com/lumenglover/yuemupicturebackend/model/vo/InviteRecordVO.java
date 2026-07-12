package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * 邀请流水明细视图对象
 */
@Data
public class InviteRecordVO implements Serializable {
    private Long id;
    private Long inviteeId;
    private String inviteeName;
    private String inviteeAvatar;
    private Integer status;
    private Date createTime;
    private Date confirmTime;
    private static final long serialVersionUID = 1L;
}
