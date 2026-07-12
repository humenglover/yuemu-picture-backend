package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.mapper.ActivityVoteMapper;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivityVoteRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Activity;
import com.lumenglover.yuemupicturebackend.model.entity.ActivitySubmission;
import com.lumenglover.yuemupicturebackend.model.entity.ActivityVote;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.service.ActivityService;
import com.lumenglover.yuemupicturebackend.service.ActivitySubmissionService;
import com.lumenglover.yuemupicturebackend.service.ActivityVoteService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动投票服务实现
 */
@Service
@Slf4j
public class ActivityVoteServiceImpl extends ServiceImpl<ActivityVoteMapper, ActivityVote>
        implements ActivityVoteService {

    @Resource
    private ActivityService activityService;

    @Resource
    private ActivitySubmissionService activitySubmissionService;

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean vote(ActivityVoteRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        Long activityId = request.getActivityId();
        List<Long> submissionIds = request.getSubmissionIds();

        ThrowUtils.throwIf(activityId == null || activityId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(CollUtil.isEmpty(submissionIds), ErrorCode.PARAMS_ERROR, "请选择要投票的作品");

        // 获取登录用户
        User loginUser = userService.getLoginUser(httpRequest);
        Long userId = loginUser.getId();

        // 1. 校验活动是否存在
        Activity activity = activityService.getById(activityId);
        ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "活动不存在");

        // 2. 校验活动是否允许投票
        ThrowUtils.throwIf(activity.getAllowVote() == null || activity.getAllowVote() != 1,
                ErrorCode.OPERATION_ERROR, "该活动不允许投票");

        // 3. 校验投票时间
        Date now = new Date();
        if (activity.getVoteStartTime() != null && now.before(activity.getVoteStartTime())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "投票尚未开始");
        }
        if (activity.getVoteEndTime() != null && now.after(activity.getVoteEndTime())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "投票已截止");
        }

        // 4. 校验投票数量
        Integer maxVotes = activity.getMaxVotesPerUser();
        Integer voteType = activity.getVoteType(); // 0-单选 1-多选

        if (voteType == 0 && submissionIds.size() > 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该活动只能单选投票");
        }

        if (maxVotes != null && submissionIds.size() > maxVotes) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "最多只能投" + maxVotes + "票");
        }

        // 5. 校验用户已投票数量
        long userVoteCount = this.getUserVoteCount(activityId, userId);
        if (maxVotes != null && userVoteCount + submissionIds.size() > maxVotes) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "您已达到最大投票数量限制（" + maxVotes + "）");
        }

        // 6. 校验提交是否存在且已通过审核
        List<ActivitySubmission> submissions = activitySubmissionService.listByIds(submissionIds);
        ThrowUtils.throwIf(submissions.size() != submissionIds.size(),
                ErrorCode.PARAMS_ERROR, "部分作品不存在");

        for (ActivitySubmission submission : submissions) {
            if (!submission.getActivityId().equals(activityId)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "作品不属于该活动");
            }
            if (submission.getStatus() != 1) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "只能投票给已通过审核的作品");
            }
        }

        // 7. 检查是否已投票并创建投票记录
        List<ActivityVote> voteList = new ArrayList<>();
        List<Long> alreadyVotedIds = new ArrayList<>();

        for (Long submissionId : submissionIds) {
            // 检查是否已有有效投票
            QueryWrapper<ActivityVote> existWrapper = new QueryWrapper<>();
            existWrapper.eq("activityId", activityId)
                    .eq("submissionId", submissionId)
                    .eq("userId", userId);
            long existCount = this.count(existWrapper);
            if (existCount > 0) {
                alreadyVotedIds.add(submissionId);
                continue;
            }

            // 创建新的投票记录
            ActivityVote vote = new ActivityVote();
            vote.setActivityId(activityId);
            vote.setSubmissionId(submissionId);
            vote.setUserId(userId);
            voteList.add(vote);
        }

        if (CollUtil.isEmpty(voteList)) {
            if (!alreadyVotedIds.isEmpty()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "您已对这些作品投过票了");
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "投票失败");
        }

        // 8. 保存投票记录
        boolean result = this.saveBatch(voteList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "投票失败");

        // 9. 更新提交的投票数
        for (ActivitySubmission submission : submissions) {
            Long subId = submission.getId();
            boolean isVoted = voteList.stream().anyMatch(v -> v.getSubmissionId().equals(subId));
            if (isVoted) {
                submission.setVoteCount(submission.getVoteCount() == null ? 1 : submission.getVoteCount() + 1);
            }
        }
        activitySubmissionService.updateBatchById(submissions);

        // 10. 更新活动投票数
        activity.setVoteCount(activity.getVoteCount() == null ? voteList.size() :
                activity.getVoteCount() + voteList.size());
        activityService.updateById(activity);

        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean cancelVote(Long activityId, Long submissionId, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(activityId == null || submissionId == null, ErrorCode.PARAMS_ERROR);

        // 获取登录用户
        User loginUser = userService.getLoginUser(httpRequest);
        Long userId = loginUser.getId();

        // 查找有效的投票记录（MyBatis-Plus会自动添加isDelete=0条件）
        QueryWrapper<ActivityVote> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("activityId", activityId)
                .eq("submissionId", submissionId)
                .eq("userId", userId);
        ActivityVote vote = this.getOne(queryWrapper);

        ThrowUtils.throwIf(vote == null, ErrorCode.NOT_FOUND_ERROR, "投票记录不存在");

        // 物理删除投票记录，避免唯一约束冲突
        QueryWrapper<ActivityVote> deleteWrapper = new QueryWrapper<>();
        deleteWrapper.eq("activityId", activityId)
                .eq("submissionId", submissionId)
                .eq("userId", userId);
        boolean result = this.remove(deleteWrapper);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "取消投票失败");

        // 更新提交的投票数
        ActivitySubmission submission = activitySubmissionService.getById(submissionId);
        if (submission != null && submission.getVoteCount() != null && submission.getVoteCount() > 0) {
            submission.setVoteCount(submission.getVoteCount() - 1);
            activitySubmissionService.updateById(submission);
        }

        // 更新活动投票数
        Activity activity = activityService.getById(activityId);
        if (activity != null && activity.getVoteCount() != null && activity.getVoteCount() > 0) {
            activity.setVoteCount(activity.getVoteCount() - 1);
            activityService.updateById(activity);
        }

        return true;
    }

    @Override
    public Set<Long> getUserVotedSubmissions(Long activityId, Long userId, List<Long> submissionIds) {
        if (activityId == null || userId == null || CollUtil.isEmpty(submissionIds)) {
            return new HashSet<>();
        }

        QueryWrapper<ActivityVote> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("activityId", activityId)
                .eq("userId", userId)
                .in("submissionId", submissionIds)
                .select("submissionId");
        // MyBatis-Plus会自动添加isDelete=0条件

        List<ActivityVote> votes = this.list(queryWrapper);
        return votes.stream()
                .map(ActivityVote::getSubmissionId)
                .collect(Collectors.toSet());
    }

    @Override
    public long getUserVoteCount(Long activityId, Long userId) {
        if (activityId == null || userId == null) {
            return 0;
        }

        QueryWrapper<ActivityVote> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("activityId", activityId)
                .eq("userId", userId);
        // MyBatis-Plus会自动添加isDelete=0条件
        return this.count(queryWrapper);
    }
}
