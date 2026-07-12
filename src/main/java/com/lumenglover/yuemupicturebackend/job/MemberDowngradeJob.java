package com.lumenglover.yuemupicturebackend.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.entity.Space;
import com.lumenglover.yuemupicturebackend.model.entity.SystemNotify;
import com.lumenglover.yuemupicturebackend.service.SpaceService;
import com.lumenglover.yuemupicturebackend.service.SystemNotifyService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会员降级定时任务
 */
@Component
@Slf4j
public class MemberDowngradeJob {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SystemNotifyService systemNotifyService;

    /**
     * 每天凌晨 0 点执行，检查并降级过期的会员
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void executeMemberDowngrade() {
        log.info("开始执行会员到期降级任务...");
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 查询出 memberType 为 Pro(1) 或 Plus(2) 的用户
        queryWrapper.in("memberType", 1, 2);
        queryWrapper.isNotNull("memberExpire");
        // 排除管理员
        queryWrapper.ne("userRole", "admin");
        // 到期时间小于当前时间，即已过期
        queryWrapper.lt("memberExpire", new Date());

        List<User> expiredUsers = userService.list(queryWrapper);
        if (expiredUsers != null && !expiredUsers.isEmpty()) {
            // 更新用户状态并发送到期通知
            for (User user : expiredUsers) {
                String oldTierName = user.getMemberType() != null && user.getMemberType() == 2 ? "Plus" : "Pro";
                userService.lambdaUpdate()
                        .set(User::getMemberType, 0)
                        .set(User::getMemberExpire, null)
                        .eq(User::getId, user.getId())
                        .update();

                // 发送会员到期降级通知
                try {
                    SystemNotify notify = new SystemNotify();
                    notify.setNotifyType("ACCOUNT_CHANGED");
                    notify.setSenderType("SYSTEM");
                    notify.setSenderId("system");
                    notify.setReceiverType("SPECIFIC_USER");
                    notify.setReceiverId(user.getId().toString());
                    notify.setTitle("您的 " + oldTierName + " 会员已到期");
                    notify.setContent("您的 " + oldTierName + " 会员权益已到期，已自动降级为普通用户，空间存储配额已同步调整。继续邀请好友即可重新获得会员权益。");
                    notify.setRelatedBizType("ACCOUNT");
                    notify.setRelatedBizId(user.getId().toString());
                    notify.setReadStatus(0);
                    notify.setIsGlobal(0);
                    notify.setIsEnabled(1);
                    systemNotifyService.addSystemNotify(notify);
                } catch (Exception e) {
                    log.error("发送降级通知失败，用户ID: {}", user.getId(), e);
                }
            }

            // 同步更新这些用户的空间存储上限为普通用户标准(50MB)，同时更新 spaceLevel, maxCount, maxSize
            List<Long> expiredUserIds = expiredUsers.stream().map(User::getId).collect(Collectors.toList());
            if (!expiredUserIds.isEmpty()) {
                com.lumenglover.yuemupicturebackend.model.enums.SpaceLevelEnum commonLevel = com.lumenglover.yuemupicturebackend.model.enums.SpaceLevelEnum.COMMON;
                int defaultStorage = commonLevel.getMaxStorage();
                spaceService.lambdaUpdate()
                        .set(Space::getMaxStorage, defaultStorage)
                        .set(Space::getSpaceLevel, commonLevel.getValue())
                        .set(Space::getMaxCount, commonLevel.getMaxCount())
                        .set(Space::getMaxSize, commonLevel.getMaxSize())
                        .in(Space::getUserId, expiredUserIds)
                        .update();
            }

            log.info("成功处理了 {} 个过期会员及其空间额度降级", expiredUsers.size());
        } else {
            log.info("今日无过期会员需要降级。");
        }
    }
}
