package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivityVoteRequest;
import com.lumenglover.yuemupicturebackend.model.entity.ActivityVote;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * 活动投票服务
 */
public interface ActivityVoteService extends IService<ActivityVote> {

    /**
     * 投票
     */
    Boolean vote(ActivityVoteRequest request, HttpServletRequest httpRequest);

    /**
     * 取消投票
     */
    Boolean cancelVote(Long activityId, Long submissionId, HttpServletRequest httpRequest);

    /**
     * 获取用户已投票的提交ID集合
     */
    Set<Long> getUserVotedSubmissions(Long activityId, Long userId, List<Long> submissionIds);

    /**
     * 获取用户在活动中的投票数量
     */
    long getUserVoteCount(Long activityId, Long userId);
}
