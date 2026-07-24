package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.entity.Post;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.vo.InteractionUserVO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.vo.ItemAnalyticsVO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.ItemAnalyticsService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.PostService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.UserService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.servlet.http.HttpServletRequest;

/**
 * 内容分析接口
 */
@RestController
@RequestMapping("/item/analytics")
@Slf4j
public class ItemAnalyticsController {

    @Resource
    private ItemAnalyticsService itemAnalyticsService;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private PostService postService;

    /**
     * 获取图片分析数据
     */
    @GetMapping("/picture/{id}")
    @SaCheckLogin
    public BaseResponse<ItemAnalyticsVO> getPictureAnalytics(@PathVariable String id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || "undefined".equals(id), ErrorCode.PARAMS_ERROR);
        Long pictureId;
        try {
            pictureId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的ID格式");
        }
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        // 权限检查：作者
        Picture picture = pictureService.getById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
        if (!java.util.Objects.equals(picture.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该图片的统计数据");
        }

        ItemAnalyticsVO analytics = itemAnalyticsService.getPictureAnalytics(pictureId);
        return ResultUtils.success(analytics);
    }

    /**
     * 获取帖子分析数据
     */
    @GetMapping("/post/{id}")
    @SaCheckLogin
    public BaseResponse<ItemAnalyticsVO> getPostAnalytics(@PathVariable String id, HttpServletRequest request) {
        ThrowUtils.throwIf(id == null || "undefined".equals(id), ErrorCode.PARAMS_ERROR);
        Long postId;
        try {
            postId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的ID格式");
        }
        ThrowUtils.throwIf(postId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        // 权限检查：作者
        Post post = postService.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR);
        if (!java.util.Objects.equals(post.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权查看该帖子的统计数据");
        }

        ItemAnalyticsVO analytics = itemAnalyticsService.getPostAnalytics(postId);
        return ResultUtils.success(analytics);
    }

    /**
     * 获取互动列表
     */
    @GetMapping("/interactions")
    @SaCheckLogin
    public BaseResponse<Page<InteractionUserVO>> getInteractionList(
            @RequestParam String targetId,
            @RequestParam Integer targetType,
            @RequestParam String type,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            HttpServletRequest request) {

        ThrowUtils.throwIf(targetId == null || "undefined".equals(targetId) || targetType == null || type == null, ErrorCode.PARAMS_ERROR);
        Long targetIdLong;
        try {
            targetIdLong = Long.parseLong(targetId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的ID格式");
        }
        User loginUser = userService.getLoginUser(request);

        // 权限检查
        // 权限检查
        if (targetType == 1) {
            Picture picture = pictureService.getById(targetIdLong);
            ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR);
            if (!java.util.Objects.equals(picture.getUserId(), loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else if (targetType == 2) {
            Post post = postService.getById(targetIdLong);
            ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR);
            if (!java.util.Objects.equals(post.getUserId(), loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }

        Page<InteractionUserVO> interactionList = itemAnalyticsService.getInteractionList(targetIdLong, targetType, type, current, size);
        return ResultUtils.success(interactionList);
    }
}
