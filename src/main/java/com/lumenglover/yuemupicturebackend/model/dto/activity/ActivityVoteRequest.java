package com.lumenglover.yuemupicturebackend.model.dto.activity;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 活动投票请求
 */
@Data
public class ActivityVoteRequest implements Serializable {

    /**
     * 活动ID
     */
    private Long activityId;

    /**
     * 提交ID列表（支持多选）
     */
    private List<Long> submissionIds;

    private static final long serialVersionUID = 1L;
}
