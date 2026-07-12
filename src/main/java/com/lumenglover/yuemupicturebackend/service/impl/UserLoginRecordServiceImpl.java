package com.lumenglover.yuemupicturebackend.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.mapper.UserLoginRecordMapper;
import com.lumenglover.yuemupicturebackend.model.dto.loginrecord.UserLoginRecordQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.SystemNotify;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.entity.UserLoginRecord;
import com.lumenglover.yuemupicturebackend.model.vo.UserLoginRecordVO;
import com.lumenglover.yuemupicturebackend.service.SystemNotifyService;
import com.lumenglover.yuemupicturebackend.service.UserLoginRecordService;
import com.lumenglover.yuemupicturebackend.utils.DeviceInfoUtil;
import com.lumenglover.yuemupicturebackend.utils.ServletUtils;
import com.lumenglover.yuemupicturebackend.utils.ip.RegionUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户登录记录服务实现
 */
@Service
@Slf4j
public class UserLoginRecordServiceImpl extends ServiceImpl<UserLoginRecordMapper, UserLoginRecord>
        implements UserLoginRecordService {

    @Resource
    private SystemNotifyService systemNotifyService;

    @Override
    public Long recordLogin(User user, String loginMethod, HttpServletRequest request) {
        if (user == null || user.getId() == null) {
            return null;
        }

        try {
            // 获取IP地址
            String loginIp = ServletUtils.getClientIP(request);

            // 解析设备信息
            DeviceInfoUtil.DeviceInfo deviceInfo = DeviceInfoUtil.parseDeviceInfo(request);

            // 获取地理位置
            String loginLocation = RegionUtils.getCityInfo(loginIp);

            // 创建登录记录
            UserLoginRecord loginRecord = new UserLoginRecord();
            loginRecord.setUserId(user.getId());
            loginRecord.setLoginTime(new Date());
            loginRecord.setLoginIp(loginIp);
            loginRecord.setLoginLocation(loginLocation);
            loginRecord.setDeviceType(deviceInfo.getDeviceType());
            loginRecord.setDeviceName(deviceInfo.getDeviceName());
            loginRecord.setOsType(deviceInfo.getOsType());
            loginRecord.setOsVersion(deviceInfo.getOsVersion());
            loginRecord.setBrowserType(deviceInfo.getBrowserType());
            loginRecord.setBrowserVersion(deviceInfo.getBrowserVersion());
            loginRecord.setUserAgent(deviceInfo.getUserAgent());
            loginRecord.setLoginStatus(1); // 成功
            loginRecord.setLoginMethod(loginMethod);
            loginRecord.setSessionId(request.getSession().getId());
            loginRecord.setIsNotified(0);

            // 检测登录风险
            int riskLevel = detectLoginRisk(user.getId(), loginIp, deviceInfo.getDeviceType());
            loginRecord.setRiskLevel(riskLevel);

            if (riskLevel > 0) {
                loginRecord.setRiskReason(getRiskReason(riskLevel, loginIp, loginLocation));
            }

            // 保存登录记录
            this.save(loginRecord);

            // 异步发送登录通知
            sendLoginNotification(loginRecord);

            return loginRecord.getId();
        } catch (Exception e) {
            log.error("记录用户登录信息失败", e);
            return null;
        }
    }

    @Override
    public void recordLoginFailure(Long userId, String loginMethod, HttpServletRequest request, String failReason) {
        try {
            String loginIp = ServletUtils.getClientIP(request);
            DeviceInfoUtil.DeviceInfo deviceInfo = DeviceInfoUtil.parseDeviceInfo(request);
            String loginLocation = RegionUtils.getCityInfo(loginIp);

            UserLoginRecord loginRecord = new UserLoginRecord();
            loginRecord.setUserId(userId);
            loginRecord.setLoginTime(new Date());
            loginRecord.setLoginIp(loginIp);
            loginRecord.setLoginLocation(loginLocation);
            loginRecord.setDeviceType(deviceInfo.getDeviceType());
            loginRecord.setDeviceName(deviceInfo.getDeviceName());
            loginRecord.setOsType(deviceInfo.getOsType());
            loginRecord.setOsVersion(deviceInfo.getOsVersion());
            loginRecord.setBrowserType(deviceInfo.getBrowserType());
            loginRecord.setBrowserVersion(deviceInfo.getBrowserVersion());
            loginRecord.setUserAgent(deviceInfo.getUserAgent());
            loginRecord.setLoginStatus(0); // 失败
            loginRecord.setLoginMethod(loginMethod);
            loginRecord.setRiskLevel(2); // 失败登录标记为高危
            loginRecord.setRiskReason(failReason);

            this.save(loginRecord);
        } catch (Exception e) {
            log.error("记录登录失败信息异常", e);
        }
    }

    @Override
    public QueryWrapper<UserLoginRecord> getQueryWrapper(UserLoginRecordQueryRequest queryRequest) {
        QueryWrapper<UserLoginRecord> queryWrapper = new QueryWrapper<>();
        if (queryRequest == null) {
            return queryWrapper;
        }

        Long userId = queryRequest.getUserId();
        String loginIp = queryRequest.getLoginIp();
        String deviceType = queryRequest.getDeviceType();
        Integer loginStatus = queryRequest.getLoginStatus();
        String loginMethod = queryRequest.getLoginMethod();
        Integer riskLevel = queryRequest.getRiskLevel();
        Date startTime = queryRequest.getStartTime();
        Date endTime = queryRequest.getEndTime();
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();

        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(StrUtil.isNotBlank(loginIp), "loginIp", loginIp);
        queryWrapper.eq(StrUtil.isNotBlank(deviceType), "deviceType", deviceType);
        queryWrapper.eq(loginStatus != null, "loginStatus", loginStatus);
        queryWrapper.eq(StrUtil.isNotBlank(loginMethod), "loginMethod", loginMethod);
        queryWrapper.eq(riskLevel != null, "riskLevel", riskLevel);
        queryWrapper.ge(startTime != null, "loginTime", startTime);
        queryWrapper.le(endTime != null, "loginTime", endTime);

        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        queryWrapper.orderByDesc("loginTime");

        return queryWrapper;
    }

    @Override
    public UserLoginRecordVO getLoginRecordVO(UserLoginRecord loginRecord, HttpServletRequest request) {
        if (loginRecord == null) {
            return null;
        }

        UserLoginRecordVO vo = new UserLoginRecordVO();
        BeanUtil.copyProperties(loginRecord, vo);

        // 判断是否为当前设备
        String currentSessionId = request.getSession().getId();
        vo.setIsCurrentDevice(currentSessionId.equals(loginRecord.getSessionId()));

        return vo;
    }

    @Override
    public Page<UserLoginRecordVO> getLoginRecordVOPage(Page<UserLoginRecord> loginRecordPage, HttpServletRequest request) {
        List<UserLoginRecord> records = loginRecordPage.getRecords();
        Page<UserLoginRecordVO> voPage = new Page<>(loginRecordPage.getCurrent(), loginRecordPage.getSize(), loginRecordPage.getTotal());

        if (records.isEmpty()) {
            return voPage;
        }

        List<UserLoginRecordVO> voList = records.stream()
                .map(record -> getLoginRecordVO(record, request))
                .collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public int detectLoginRisk(Long userId, String loginIp, String deviceInfo) {
        try {
            // 查询最近的登录记录
            QueryWrapper<UserLoginRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId);
            queryWrapper.eq("loginStatus", 1);
            queryWrapper.orderByDesc("loginTime");
            queryWrapper.last("LIMIT 10");

            List<UserLoginRecord> recentLogins = this.list(queryWrapper);

            if (recentLogins.isEmpty()) {
                // 首次登录，正常
                return 0;
            }

            // 检查是否为常用IP
            boolean isCommonIp = recentLogins.stream()
                    .anyMatch(record -> loginIp.equals(record.getLoginIp()));

            // 检查是否为常用设备
            boolean isCommonDevice = recentLogins.stream()
                    .anyMatch(record -> deviceInfo.equals(record.getDeviceType()));

            // 检查短时间内登录次数
            long recentLoginCount = recentLogins.stream()
                    .filter(record -> {
                        long timeDiff = System.currentTimeMillis() - record.getLoginTime().getTime();
                        return timeDiff < 3600000; // 1小时内
                    })
                    .count();

            // 风险评估
            if (!isCommonIp && !isCommonDevice) {
                return 2; // 高危：新IP且新设备
            } else if (!isCommonIp || !isCommonDevice) {
                return 1; // 可疑：新IP或新设备
            } else if (recentLoginCount > 5) {
                return 1; // 可疑：频繁登录
            }

            return 0; // 正常
        } catch (Exception e) {
            log.error("检测登录风险失败", e);
            return 0;
        }
    }

    @Override
    public void sendLoginNotification(UserLoginRecord loginRecord) {
        try {
            // 根据风险等级设置不同的通知标题和图标
            String title;
            String icon;

            if (loginRecord.getRiskLevel() == 2) {
                title = "⚠️ 账号安全提醒：检测到异常登录";
                icon = "alert";
            } else if (loginRecord.getRiskLevel() == 1) {
                title = "🔔 登录提醒：新设备登录";
                icon = "announce";
            } else {
                title = "✅ 登录成功通知";
                icon = "info";
            }

            // 格式化时间为友好格式
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy年MM月dd日 HH:mm:ss");
            String formattedTime = sdf.format(loginRecord.getLoginTime());

            // 构建通知内容
            StringBuilder content = new StringBuilder();
            content.append("您的账号于 ").append(formattedTime).append(" 登录成功\n\n");
            content.append("📍 登录信息：\n");
            content.append("• 登录地点：").append(loginRecord.getLoginLocation()).append("\n");
            content.append("• 登录IP：").append(loginRecord.getLoginIp()).append("\n");
            content.append("• 设备类型：").append(loginRecord.getDeviceType()).append("\n");
            content.append("• 操作系统：").append(loginRecord.getOsType()).append(" ").append(loginRecord.getOsVersion()).append("\n");
            content.append("• 浏览器：").append(loginRecord.getBrowserType()).append(" ").append(loginRecord.getBrowserVersion()).append("\n");
            content.append("• 登录方式：").append(getLoginMethodText(loginRecord.getLoginMethod())).append("\n\n");

            // 根据风险等级添加不同的提示
            if (loginRecord.getRiskLevel() == 2) {
                content.append("⚠️ 警告：检测到异常登录行为！\n");
                content.append("如非本人操作，请立即修改密码并检查账号安全！");
            } else if (loginRecord.getRiskLevel() == 1) {
                content.append("💡 提示：检测到新设备或新地点登录\n");
                content.append("如非本人操作，请注意账号安全。");
            } else {
                content.append("✨ 欢迎回来！如非本人操作，请及时修改密码。");
            }

            // 创建系统通知
            SystemNotify systemNotify = new SystemNotify();
            systemNotify.setOperatorId("system");
            systemNotify.setOperatorType("SYSTEM");
            systemNotify.setNotifyType("ACCOUNT_CHANGED");
            systemNotify.setSenderType("SYSTEM");
            systemNotify.setSenderId("system");
            systemNotify.setReceiverType("SPECIFIC_USER");
            systemNotify.setReceiverId(String.valueOf(loginRecord.getUserId()));
            systemNotify.setTitle(title);
            systemNotify.setContent(content.toString());
            systemNotify.setNotifyIcon(icon);
            systemNotify.setRelatedBizType("LOGIN");
            systemNotify.setRelatedBizId(String.valueOf(loginRecord.getId()));
            systemNotify.setReadStatus(0);
            systemNotify.setIsGlobal(0);
            systemNotify.setIsEnabled(1);

            // 保存通知
            systemNotifyService.addSystemNotify(systemNotify);

            // 更新通知状态
            loginRecord.setIsNotified(1);
            this.updateById(loginRecord);

        } catch (Exception e) {
            log.error("发送登录通知失败", e);
        }
    }

    /**
     * 获取登录方式文本
     */
    private String getLoginMethodText(String loginMethod) {
        if (loginMethod == null) {
            return "未知";
        }
        switch (loginMethod) {
            case "PASSWORD":
                return "密码登录";
            case "WECHAT":
                return "微信登录";
            case "QQ":
                return "QQ登录";
            case "EMAIL":
                return "邮箱登录";
            case "PHONE":
                return "手机号登录";
            default:
                return loginMethod;
        }
    }

    /**
     * 获取风险原因描述
     */
    private String getRiskReason(int riskLevel, String loginIp, String loginLocation) {
        if (riskLevel == 2) {
            return "新设备和新IP地址登录：" + loginLocation + "(" + loginIp + ")";
        } else if (riskLevel == 1) {
            return "检测到新设备或新IP地址登录：" + loginLocation + "(" + loginIp + ")";
        }
        return null;
    }

    @Override
    public boolean deleteLoginRecordAndKickout(Long id, Long currentUserId) {
        if (id == null || currentUserId == null) {
            return false;
        }

        try {
            // 获取登录记录
            UserLoginRecord loginRecord = this.getById(id);
            if (loginRecord == null) {
                return false;
            }

            // 验证权限：只能删除自己的记录
            if (!loginRecord.getUserId().equals(currentUserId)) {
                log.warn("用户 {} 尝试删除其他用户的登录记录 {}", currentUserId, id);
                return false;
            }

            // 如果有sessionId，踢出对应的会话
            if (StrUtil.isNotBlank(loginRecord.getSessionId())) {
                try {
                    // 使用SaToken踢出指定用户的指定设备
                    StpUtil.kickoutByTokenValue(loginRecord.getSessionId());
                    log.info("已踢出用户 {} 的会话，sessionId: {}", loginRecord.getUserId(), loginRecord.getSessionId());
                } catch (Exception e) {
                    log.warn("踢出会话失败，sessionId可能已过期: {}", loginRecord.getSessionId(), e);
                    // 即使踢出失败也继续删除记录
                }
            }

            // 删除登录记录
            boolean result = this.removeById(id);
            if (result) {
                log.info("用户 {} 删除了登录记录 {}", currentUserId, id);
            }
            return result;
        } catch (Exception e) {
            log.error("删除登录记录并踢出会话失败", e);
            return false;
        }
    }

    @Override
    public boolean batchDeleteLoginRecordAndKickout(Long[] ids, Long currentUserId) {
        if (ids == null || ids.length == 0 || currentUserId == null) {
            return false;
        }

        try {
            int successCount = 0;
            for (Long id : ids) {
                if (deleteLoginRecordAndKickout(id, currentUserId)) {
                    successCount++;
                }
            }

            log.info("用户 {} 批量删除登录记录，成功 {}/{}", currentUserId, successCount, ids.length);
            return successCount > 0;
        } catch (Exception e) {
            log.error("批量删除登录记录并踢出会话失败", e);
            return false;
        }
    }
}
