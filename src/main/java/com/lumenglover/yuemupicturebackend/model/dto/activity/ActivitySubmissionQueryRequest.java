package com.lumenglover.yuemupicturebackend.model.dto.activity;

import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 活动提交查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ActivitySubmissionQueryRequest extends PageRequest implements Serializable {

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态：0-待审核 1-已通过 2-已拒绝
     */
    private Integer status;

    /**
     * 排序字段：voteCount-按票数排序
     */
    private String sortField;

    private static final long serialVersionUID = 1L;
}
