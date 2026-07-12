package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 邀请流水表
 * @TableName invite_record
 */
@TableName(value ="invite_record")
@Data
public class InviteRecord implements Serializable {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 邀请人ID
     */
    private Long inviterId;

    /**
     * 被邀请人ID
     */
    private Long inviteeId;

    /**
     * 本次邀请使用的邀请码
     */
    private String inviteCode;

    /**
     * 0=无效邀请，1=有效邀请
     */
    private Integer status;

    /**
     * 邀请记录创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 被邀请人注册完成时间
     */
    private Date confirmTime;

    /**
     * 软删除标识，0=正常，1=已删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
