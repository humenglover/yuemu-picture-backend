package com.lumenglover.yuemupicturebackend.controller;

import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.model.dto.picturelike.PictureLikeRequest;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.service.PicturelikeService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/picturelike")
public class PictureLikeController {
    @Resource
    private PicturelikeService pictureLikeService;

    @Resource
    private UserService userService;

    /**
     * 用户点赞
     */
    @PostMapping("/like")
    public BaseResponse<Boolean> UserLike(@RequestBody PictureLikeRequest pictureLikeRequest, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        Long userId = user.getId();
        try {
            CompletableFuture<Boolean> future = pictureLikeService.UserLike(pictureLikeRequest, userId);
            // 这里不等待结果，直接返回成功
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("Error in UserLike controller: ", e);
            return (BaseResponse<Boolean>) ResultUtils.error(ErrorCode.SYSTEM_ERROR, "点赞操作失败");
        }
    }

    /**
     * 用户分享
     */
    @PostMapping("/share/{pictureId}")
    public BaseResponse<Boolean> UserShare(@PathVariable String pictureId, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_LOGIN_ERROR);
        Long userId = user.getId();
        try {
            CompletableFuture<Boolean> future = pictureLikeService.UserShare(pictureId, userId);
            // 这里不等待结果，直接返回成功
            return ResultUtils.success(true);
        } catch (Exception e) {
            log.error("Error in UserShare controller: ", e);
            return (BaseResponse<Boolean>) ResultUtils.error(ErrorCode.SYSTEM_ERROR, "分享操作失败");
        }
    }
}
