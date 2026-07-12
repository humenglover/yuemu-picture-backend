package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 活动提交记录表
 */
@TableName(value = "activity_submission")
@Data
public class ActivitySubmission implements Serializable {
    /**
     * 提交ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 提交用户ID
     */
    private Long userId;

    /**
     * 图片ID
     */
    private Long pictureId;

    /**
     * 提交标题
     */
    private String submissionTitle;

    /**
     * 提交描述
     */
    private String submissionDesc;

    /**
     * 状态：0-待审核 1-已通过 2-已拒绝
     */
    private Integer status;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 获得票数
     */
    private Integer voteCount;

    /**
     * 排名
     */
    private Integer ranking;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 是否删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
