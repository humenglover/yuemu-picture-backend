package com.lumenglover.yuemupicturebackend.service;

import com.lumenglover.yuemupicturebackend.model.dto.copyright.CopyrightRegisterRequest;
import com.lumenglover.yuemupicturebackend.model.dto.copyright.CopyrightTraceRequest;
import com.lumenglover.yuemupicturebackend.model.vo.CopyrightInfoVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 图片版权服务
 */
public interface PictureCopyrightService {

    /**
     * 申请版权登记
     *
     * @param registerRequest 登记请求
     * @param request HTTP请求
     * @return 申请ID
     */
    Long registerCopyright(CopyrightRegisterRequest registerRequest, HttpServletRequest request);

    /**
     * 更新版权信息
     *
     * @param updateRequest 更新请求
     * @param request HTTP请求
     * @return 是否成功
     */
    Boolean updateCopyright(CopyrightRegisterRequest updateRequest, HttpServletRequest request);

    /**
     * 版权溯源查询
     *
     * @param traceRequest 溯源请求
     * @param request HTTP请求
     * @return 版权信息
     */
    CopyrightInfoVO traceCopyright(CopyrightTraceRequest traceRequest, HttpServletRequest request);

    /**
     * 生成版权溯源码
     *
     * @param pictureId 图片ID
     * @param userId 用户ID
     * @return 溯源码
     */
    String generateCopyrightCode(Long pictureId, Long userId);

    /**
     * 根据图片ID获取版权信息
     *
     * @param pictureId 图片ID
     * @return 版权信息
     */
    CopyrightInfoVO getCopyrightInfoByPictureId(Long pictureId);
}
