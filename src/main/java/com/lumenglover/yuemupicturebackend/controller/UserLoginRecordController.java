package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.dto.loginrecord.UserLoginRecordQueryRequest;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.entity.UserLoginRecord;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.vo.UserLoginRecordVO;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.UserLoginRecordService;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.UserService;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckRole;
import javax.servlet.http.HttpServletRequest;

/**
 * 用户登录记录控制器
 */
@RestController
@RequestMapping("/loginRecord")
@Slf4j
public class UserLoginRecordController {

    @Resource
    private UserLoginRecordService userLoginRecordService;

    @Resource
    private UserService userService;

    /**
     * 分页获取当前用户的登录记录
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<UserLoginRecordVO>> listMyLoginRecordByPage(
            @RequestBody UserLoginRecordQueryRequest queryRequest,
            HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        queryRequest.setUserId(loginUser.getId());

        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();

        Page<UserLoginRecord> loginRecordPage = userLoginRecordService.page(
                new Page<>(current, size),
                userLoginRecordService.getQueryWrapper(queryRequest)
        );

        Page<UserLoginRecordVO> voPage = userLoginRecordService.getLoginRecordVOPage(loginRecordPage, request);
        return ResultUtils.success(voPage);
    }

    /**
     * 分页获取用户登录记录（管理员）
     */
    @PostMapping("/list/page/vo/admin")
    @SaCheckRole("admin")
    public BaseResponse<Page<UserLoginRecordVO>> listLoginRecordByPage(
            @RequestBody UserLoginRecordQueryRequest queryRequest,
            HttpServletRequest request) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();

        Page<UserLoginRecord> loginRecordPage = userLoginRecordService.page(
                new Page<>(current, size),
                userLoginRecordService.getQueryWrapper(queryRequest)
        );

        Page<UserLoginRecordVO> voPage = userLoginRecordService.getLoginRecordVOPage(loginRecordPage, request);
        return ResultUtils.success(voPage);
    }

    /**
     * 根据ID获取登录记录
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserLoginRecordVO> getLoginRecordVOById(Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        UserLoginRecord loginRecord = userLoginRecordService.getById(id);
        if (loginRecord == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        // 只能查看自己的登录记录，管理员除外
        if (!loginRecord.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        UserLoginRecordVO vo = userLoginRecordService.getLoginRecordVO(loginRecord, request);
        return ResultUtils.success(vo);
    }

    /**
     * 删除登录记录
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteLoginRecord(@RequestBody Long id, HttpServletRequest request) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);
        UserLoginRecord loginRecord = userLoginRecordService.getById(id);

        if (loginRecord == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }

        // 只能删除自己的登录记录，管理员除外
        if (!loginRecord.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        boolean result = userLoginRecordService.removeById(id);
        return ResultUtils.success(result);
    }

    /**
     * 批量删除登录记录
     */
    @PostMapping("/delete/batch")
    public BaseResponse<Boolean> batchDeleteLoginRecord(@RequestBody Long[] ids, HttpServletRequest request) {
        if (ids == null || ids.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        User loginUser = userService.getLoginUser(request);

        // 验证权限
        for (Long id : ids) {
            UserLoginRecord loginRecord = userLoginRecordService.getById(id);
            if (loginRecord != null &&
                !loginRecord.getUserId().equals(loginUser.getId()) &&
                !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权删除他人的登录记录");
            }
        }

        boolean result = userLoginRecordService.removeBatchByIds(java.util.Arrays.asList(ids));
        return ResultUtils.success(result);
    }
}
