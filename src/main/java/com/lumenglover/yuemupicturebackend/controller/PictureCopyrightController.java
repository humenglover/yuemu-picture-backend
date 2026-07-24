package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.dto.copyright.CopyrightRegisterRequest;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.dto.copyright.CopyrightTraceRequest;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.model.vo.CopyrightInfoVO;
import cn.dev33.satoken.annotation.SaCheckLogin;
import com.lumenglover.yuemupicturebackend.service.PictureCopyrightService;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.annotations.Api;
import cn.dev33.satoken.annotation.SaCheckLogin;
import io.swagger.annotations.ApiOperation;
import cn.dev33.satoken.annotation.SaCheckLogin;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckLogin;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckLogin;
import javax.servlet.http.HttpServletRequest;

/**
 * 图片版权接口
 */
@RestController
@RequestMapping("/picture/copyright")
@Slf4j
public class PictureCopyrightController {

    @Resource
    private PictureCopyrightService copyrightService;

    /**
     * 申请版权登记
     */
    @PostMapping("/register")
    @SaCheckLogin
    public BaseResponse<Long> registerCopyright(@RequestBody CopyrightRegisterRequest registerRequest,
                                                  HttpServletRequest request) {
        Long copyrightId = copyrightService.registerCopyright(registerRequest, request);
        return ResultUtils.success(copyrightId);
    }

    /**
     * 更新版权信息
     */
    @PostMapping("/update")
    @SaCheckLogin
    public BaseResponse<Boolean> updateCopyright(@RequestBody CopyrightRegisterRequest updateRequest,
                                                   HttpServletRequest request) {
        Boolean result = copyrightService.updateCopyright(updateRequest, request);
        return ResultUtils.success(result);
    }

    /**
     * 版权溯源查询（无需登录）
     */
    @PostMapping("/trace")
    public BaseResponse<CopyrightInfoVO> traceCopyright(@RequestBody CopyrightTraceRequest traceRequest,
                                                         HttpServletRequest request) {
        CopyrightInfoVO copyrightInfo = copyrightService.traceCopyright(traceRequest, request);
        return ResultUtils.success(copyrightInfo);
    }

    /**
     * 根据图片ID获取版权信息
     */
    @GetMapping("/get")
    public BaseResponse<CopyrightInfoVO> getCopyrightByPictureId(@RequestParam String pictureId) {

        CopyrightInfoVO copyrightInfo = copyrightService.getCopyrightInfoByPictureId(Long.valueOf(pictureId));
        return ResultUtils.success(copyrightInfo);
    }
}
