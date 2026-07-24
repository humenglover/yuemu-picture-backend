package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.vo.CreatorAnalyticsVO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.CreatorAnalyticsService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.UserService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.GetMapping;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.RequestMapping;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.servlet.http.HttpServletRequest;

/**
 * 创作者数据分析接口
 */
@RestController
@RequestMapping("/creator/analytics")
@Slf4j
public class CreatorAnalyticsController {

    @Resource
    private CreatorAnalyticsService creatorAnalyticsService;

    @Resource
    private UserService userService;

    /**
     * 获取当前用户的数据分析
     */
    @GetMapping("/my")
    @SaCheckLogin
    public BaseResponse<CreatorAnalyticsVO> getMyAnalytics(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        CreatorAnalyticsVO analytics = creatorAnalyticsService.getCreatorAnalytics(loginUser.getId());
        return ResultUtils.success(analytics);
    }

    /**
     * 获取指定用户的数据分析（管理员可查看任何用户）
     */
    @GetMapping("/user")
    public BaseResponse<CreatorAnalyticsVO> getUserAnalytics(Long userId, HttpServletRequest request) {
        User loginUser = userService.isLogin(request);
        if (loginUser == null) {
            return (BaseResponse<CreatorAnalyticsVO>) ResultUtils.error(403, "请登录后查看数据");
        }

        // 只能查看自己的数据，除非是管理员
        if (!loginUser.getId().equals(userId) && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            return (BaseResponse<CreatorAnalyticsVO>) ResultUtils.error(403, "无权查看他人数据");
        }

        CreatorAnalyticsVO analytics = creatorAnalyticsService.getCreatorAnalytics(userId);
        return ResultUtils.success(analytics);
    }
}
