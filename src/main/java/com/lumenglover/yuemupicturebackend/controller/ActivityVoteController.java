package com.lumenglover.yuemupicturebackend.controller;

import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivityVoteRequest;
import com.lumenglover.yuemupicturebackend.service.ActivityVoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;

/**
 * 活动投票接口
 */
@RestController
@RequestMapping("/activity/vote")
@Slf4j
public class ActivityVoteController {

    @Resource
    private ActivityVoteService activityVoteService;

    /**
     * 投票
     */
    @PostMapping("/submit")
    public BaseResponse<Boolean> vote(@RequestBody ActivityVoteRequest request,
                                      HttpServletRequest httpRequest) {
        Boolean result = activityVoteService.vote(request, httpRequest);
        return ResultUtils.success(result);
    }

    /**
     * 取消投票
     */
    @DeleteMapping("/cancel")
    public BaseResponse<Boolean> cancelVote(@RequestParam Long activityId,
                                            @RequestParam Long submissionId,
                                            HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(activityId == null || submissionId == null, ErrorCode.PARAMS_ERROR);
        Boolean result = activityVoteService.cancelVote(activityId, submissionId, httpRequest);
        return ResultUtils.success(result);
    }

    /**
     * 获取用户已投票的提交ID列表
     */
    @GetMapping("/my/voted")
    public BaseResponse<Set<Long>> getMyVotedSubmissions(@RequestParam Long activityId,
                                                          @RequestParam List<Long> submissionIds,
                                                          HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(activityId == null, ErrorCode.PARAMS_ERROR);

        // 获取当前用户
        Long userId = null;
        try {
            userId = httpRequest.getAttribute("userId") != null ?
                    (Long) httpRequest.getAttribute("userId") : null;
        } catch (Exception e) {
            return ResultUtils.success(Set.of());
        }

        if (userId == null) {
            return ResultUtils.success(Set.of());
        }

        Set<Long> votedSubmissions = activityVoteService.getUserVotedSubmissions(
                activityId, userId, submissionIds);
        return ResultUtils.success(votedSubmissions);
    }

    /**
     * 获取用户在某活动的投票数量
     */
    @GetMapping("/my/count")
    public BaseResponse<Long> getMyVoteCount(@RequestParam Long activityId,
                                             HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(activityId == null, ErrorCode.PARAMS_ERROR);

        // 获取当前用户
        Long userId = null;
        try {
            userId = httpRequest.getAttribute("userId") != null ?
                    (Long) httpRequest.getAttribute("userId") : null;
        } catch (Exception e) {
            return ResultUtils.success(0L);
        }

        if (userId == null) {
            return ResultUtils.success(0L);
        }

        long count = activityVoteService.getUserVoteCount(activityId, userId);
        return ResultUtils.success(count);
    }
}
