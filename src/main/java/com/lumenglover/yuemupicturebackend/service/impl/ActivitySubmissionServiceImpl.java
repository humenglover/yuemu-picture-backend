package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.mapper.ActivitySubmissionMapper;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivitySubmissionAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivitySubmissionQueryRequest;
import com.lumenglover.yuemupicturebackend.model.dto.activity.ActivitySubmissionReviewRequest;
import com.lumenglover.yuemupicturebackend.model.entity.*;
import com.lumenglover.yuemupicturebackend.model.vo.ActivitySubmissionVO;
import com.lumenglover.yuemupicturebackend.model.vo.PictureVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动提交服务实现
 */
@Service
@Slf4j
public class ActivitySubmissionServiceImpl extends ServiceImpl<ActivitySubmissionMapper, ActivitySubmission>
        implements ActivitySubmissionService {

    @Resource
    private ActivityService activityService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ActivityVoteService activityVoteService;

    @Resource
    private SystemNotifyService systemNotifyService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitToActivity(ActivitySubmissionAddRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        Long activityId = request.getActivityId();
        Long pictureId = request.getPictureId();

        // 获取登录用户
        User loginUser = userService.getLoginUser(httpRequest);
        Long userId = loginUser.getId();

        // 1. 校验活动是否存在
        Activity activity = activityService.getById(activityId);
        ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "活动不存在");

        // 2. 校验活动是否允许提交
        ThrowUtils.throwIf(activity.getAllowSubmission() == null || activity.getAllowSubmission() != 1,
                ErrorCode.OPERATION_ERROR, "该活动不允许提交");

        // 3. 校验提交时间
        Date now = new Date();
        if (activity.getSubmissionStartTime() != null && now.before(activity.getSubmissionStartTime())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交尚未开始");
        }
        if (activity.getSubmissionEndTime() != null && now.after(activity.getSubmissionEndTime())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "提交已截止");
        }

        // 4. 校验用户提交数量
        QueryWrapper<ActivitySubmission> countWrapper = new QueryWrapper<>();
        countWrapper.eq("activityId", activityId).eq("userId", userId);
        long userSubmissionCount = this.count(countWrapper);

        Integer maxSubmissions = activity.getMaxSubmissionsPerUser();
        if (maxSubmissions != null && userSubmissionCount >= maxSubmissions) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "您已达到最大提交数量限制（" + maxSubmissions + "）");
        }

        // 5. 校验图片是否存在
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 6. 校验图片是否已提交
        QueryWrapper<ActivitySubmission> existWrapper = new QueryWrapper<>();
        existWrapper.eq("activityId", activityId)
                .eq("userId", userId)
                .eq("pictureId", pictureId);
        long existCount = this.count(existWrapper);
        ThrowUtils.throwIf(existCount > 0, ErrorCode.OPERATION_ERROR, "该图片已提交过");

        // 7. 创建提交记录
        ActivitySubmission submission = new ActivitySubmission();
        submission.setActivityId(activityId);
        submission.setUserId(userId);
        submission.setPictureId(pictureId);
        submission.setSubmissionTitle(request.getSubmissionTitle());
        submission.setSubmissionDesc(request.getSubmissionDesc());

        // 如果活动设置了不需要审核，则直接通过
        if (activity.getIsNeedAudit() != null && activity.getIsNeedAudit() == 0) {
            submission.setStatus(1); // 已通过
            submission.setReviewMessage("活动未开启审核，系统自动通过");
        } else {
            submission.setStatus(0); // 待审核
        }

        submission.setVoteCount(0);

        boolean result = this.save(submission);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "提交失败");

        // 8. 更新活动提交数量
        activity.setSubmissionCount(activity.getSubmissionCount() == null ? 1 : activity.getSubmissionCount() + 1);
        activityService.updateById(activity);

        // 9. 发送通知给活动创建者
        sendSubmissionNotifyToCreator(activity, loginUser, submission);

        return submission.getId();
    }

    /**
     * 发送提交通知给活动创建者
     */
    private void sendSubmissionNotifyToCreator(Activity activity, User submitter, ActivitySubmission submission) {
        try {
            // 如果提交者就是创建者，不发送通知
            if (activity.getUserId().equals(submitter.getId())) {
                return;
            }

            SystemNotify notify = new SystemNotify();
            notify.setSenderType("SYSTEM");
            notify.setSenderId("system");
            notify.setReceiverType("SPECIFIC_USER");
            notify.setReceiverId(String.valueOf(activity.getUserId()));
            notify.setNotifyType("activity_submission");
            notify.setTitle("活动收到新的作品提交");
            notify.setContent(String.format("用户 %s 向您的活动《%s》提交了作品，请及时审核。",
                    submitter.getUserName(), activity.getTitle()));
            notify.setRelatedBizType("activity_submission");
            notify.setRelatedBizId(String.valueOf(submission.getId()));
            notify.setReadStatus(0);
            notify.setIsGlobal(0);
            notify.setIsEnabled(1);
            notify.setOperatorType("SYSTEM");
            notify.setOperatorId("system");

            systemNotifyService.addSystemNotify(notify);
            log.info("发送活动提交通知成功，活动ID: {}, 提交ID: {}", activity.getId(), submission.getId());
        } catch (Exception e) {
            log.error("发送活动提交通知失败", e);
            // 通知失败不影响主流程
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean reviewSubmission(ActivitySubmissionReviewRequest request, HttpServletRequest httpRequest) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        Long submissionId = request.getId();
        Integer status = request.getStatus();

        // 校验参数
        ThrowUtils.throwIf(submissionId == null || submissionId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(status == null || (status != 1 && status != 2),
                ErrorCode.PARAMS_ERROR, "状态只能是1(通过)或2(拒绝)");

        // 校验权限（需要是管理员）
        User loginUser = userService.getLoginUser(httpRequest);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);

        // 获取提交记录
        ActivitySubmission submission = this.getById(submissionId);
        ThrowUtils.throwIf(submission == null, ErrorCode.NOT_FOUND_ERROR, "提交记录不存在");
        ThrowUtils.throwIf(submission.getStatus() != 0, ErrorCode.OPERATION_ERROR, "该提交已审核");

        // 获取活动信息
        Activity activity = activityService.getById(submission.getActivityId());
        ThrowUtils.throwIf(activity == null, ErrorCode.NOT_FOUND_ERROR, "活动不存在");

        // 更新状态
        submission.setStatus(status);
        submission.setReviewMessage(request.getReviewMessage());

        boolean result = this.updateById(submission);

        // 发送审核结果通知给提交者
        if (result) {
            sendReviewNotifyToSubmitter(activity, submission, status, request.getReviewMessage());
        }

        return result;
    }

    /**
     * 发送审核结果通知给提交者
     */
    private void sendReviewNotifyToSubmitter(Activity activity, ActivitySubmission submission,
                                             Integer status, String reviewMessage) {
        try {
            SystemNotify notify = new SystemNotify();
            notify.setSenderType("SYSTEM");
            notify.setSenderId("system");
            notify.setReceiverType("SPECIFIC_USER");
            notify.setReceiverId(String.valueOf(submission.getUserId()));
            notify.setNotifyType("activity_review");

            if (status == 1) {
                notify.setTitle("活动作品审核通过");
                notify.setContent(String.format("恭喜！您提交到活动《%s》的作品已通过审核。", activity.getTitle()));
            } else {
                notify.setTitle("活动作品审核未通过");
                String reason = StringUtils.isNotBlank(reviewMessage) ? reviewMessage : "未说明原因";
                notify.setContent(String.format("很抱歉，您提交到活动《%s》的作品未通过审核。原因：%s",
                        activity.getTitle(), reason));
            }

            notify.setRelatedBizType("activity_submission");
            notify.setRelatedBizId(String.valueOf(submission.getId()));
            notify.setReadStatus(0);
            notify.setIsGlobal(0);
            notify.setIsEnabled(1);
            notify.setOperatorType("SYSTEM");
            notify.setOperatorId("system");

            systemNotifyService.addSystemNotify(notify);
            log.info("发送活动审核通知成功，提交ID: {}, 状态: {}", submission.getId(), status);
        } catch (Exception e) {
            log.error("发送活动审核通知失败", e);
            // 通知失败不影响主流程
        }
    }

    @Override
    public QueryWrapper<ActivitySubmission> getQueryWrapper(ActivitySubmissionQueryRequest request) {
        QueryWrapper<ActivitySubmission> queryWrapper = new QueryWrapper<>();
        if (request == null) {
            return queryWrapper;
        }

        Long activityId = request.getActivityId();
        Long userId = request.getUserId();
        Integer status = request.getStatus();
        String sortField = request.getSortField();
        String sortOrder = request.getSortOrder();

        // 拼接查询条件
        queryWrapper.eq(activityId != null, "activityId", activityId);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(status != null, "status", status);

        // 排序
        if (StringUtils.isNotBlank(sortField)) {
            if ("voteCount".equals(sortField)) {
                queryWrapper.orderBy(true, "asc".equals(sortOrder), "voteCount");
            } else {
                queryWrapper.orderBy(true, "asc".equals(sortOrder), sortField);
            }
        } else {
            // 默认按创建时间倒序
            queryWrapper.orderByDesc("createTime");
        }

        return queryWrapper;
    }

    @Override
    public Page<ActivitySubmissionVO> getSubmissionVOPage(Page<ActivitySubmission> submissionPage,
                                                          HttpServletRequest httpRequest) {
        List<ActivitySubmission> submissionList = submissionPage.getRecords();
        Page<ActivitySubmissionVO> submissionVOPage = new Page<>(
                submissionPage.getCurrent(), submissionPage.getSize(), submissionPage.getTotal());

        if (CollUtil.isEmpty(submissionList)) {
            return submissionVOPage;
        }

        // 批量获取用户信息
        Set<Long> userIdSet = submissionList.stream()
                .map(ActivitySubmission::getUserId)
                .collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 批量获取图片信息
        Set<Long> pictureIdSet = submissionList.stream()
                .map(ActivitySubmission::getPictureId)
                .collect(Collectors.toSet());
        Map<Long, List<Picture>> pictureIdPictureListMap = pictureService.listByIds(pictureIdSet).stream()
                .collect(Collectors.groupingBy(Picture::getId));

        // 获取当前用户的投票记录
        User loginUser = null;
        Set<Long> votedSubmissionIds = new HashSet<>();
        try {
            loginUser = userService.getLoginUser(httpRequest);
            if (loginUser != null) {
                List<Long> submissionIds = submissionList.stream()
                        .map(ActivitySubmission::getId)
                        .collect(Collectors.toList());
                votedSubmissionIds = activityVoteService.getUserVotedSubmissions(
                        submissionList.get(0).getActivityId(), loginUser.getId(), submissionIds);
            }
        } catch (Exception e) {
            // 未登录，忽略
        }

        // 填充信息
        List<ActivitySubmissionVO> submissionVOList = new ArrayList<>();
        for (ActivitySubmission submission : submissionList) {
            ActivitySubmissionVO submissionVO = this.getSubmissionVO(submission, httpRequest);

            // 设置用户信息
            Long userId = submission.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            submissionVO.setUser(userService.getUserVO(user));

            // 设置图片信息
            Long pictureId = submission.getPictureId();
            Picture picture = null;
            if (pictureIdPictureListMap.containsKey(pictureId)) {
                picture = pictureIdPictureListMap.get(pictureId).get(0);
            }
            submissionVO.setPicture(PictureVO.objToVo(picture));

            // 设置是否已投票
            submissionVO.setHasVoted(votedSubmissionIds.contains(submission.getId()));

            submissionVOList.add(submissionVO);
        }

        submissionVOPage.setRecords(submissionVOList);
        return submissionVOPage;
    }

    @Override
    public ActivitySubmissionVO getSubmissionVO(ActivitySubmission submission, HttpServletRequest httpRequest) {
        ActivitySubmissionVO submissionVO = new ActivitySubmissionVO();
        BeanUtils.copyProperties(submission, submissionVO);
        return submissionVO;
    }
}
