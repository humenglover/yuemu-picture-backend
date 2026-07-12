package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.mapper.PictureCopyrightMapper;
import com.lumenglover.yuemupicturebackend.mapper.PictureCopyrightTraceMapper;
import com.lumenglover.yuemupicturebackend.mapper.PictureMapper;
import com.lumenglover.yuemupicturebackend.model.dto.copyright.CopyrightRegisterRequest;
import com.lumenglover.yuemupicturebackend.model.dto.copyright.CopyrightTraceRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.PictureCopyright;
import com.lumenglover.yuemupicturebackend.model.entity.PictureCopyrightTrace;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.CopyrightInfoVO;
import com.lumenglover.yuemupicturebackend.service.PictureCopyrightService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import com.lumenglover.yuemupicturebackend.utils.ServletUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * 图片版权服务实现
 */
@Service
@Slf4j
public class PictureCopyrightServiceImpl implements PictureCopyrightService {

    @Resource
    private PictureCopyrightMapper copyrightMapper;

    @Resource
    private PictureCopyrightTraceMapper traceMapper;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private UserService userService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long registerCopyright(CopyrightRegisterRequest registerRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(registerRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = registerRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片ID不能为空");

        String copyrightOwner = registerRequest.getCopyrightOwner();
        ThrowUtils.throwIf(StringUtils.isBlank(copyrightOwner), ErrorCode.PARAMS_ERROR, "版权所有者姓名不能为空");

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 验证图片是否存在且属于当前用户
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        ThrowUtils.throwIf(!picture.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "只能为自己的图片申请版权");

        // 检查是否已经登记过版权 - 使用驼峰命名
        QueryWrapper<PictureCopyright> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pictureId", pictureId);  // 使用驼峰命名
        PictureCopyright existCopyright = copyrightMapper.selectOne(queryWrapper);
        ThrowUtils.throwIf(existCopyright != null, ErrorCode.OPERATION_ERROR, "该图片已登记版权");

        // 生成版权溯源码
        String copyrightCode = generateCopyrightCode(pictureId, loginUser.getId());

        // 创建版权记录
        PictureCopyright copyright = new PictureCopyright();
        copyright.setPictureId(pictureId);
        copyright.setUserId(loginUser.getId());
        copyright.setCopyrightCode(copyrightCode);
        copyright.setCopyrightOwner(copyrightOwner);
        copyright.setCopyrightDesc(registerRequest.getCopyrightDesc());
        copyright.setAllowCommercial(registerRequest.getAllowCommercial() != null ? registerRequest.getAllowCommercial() : 0);
        copyright.setRequireAttribution(registerRequest.getRequireAttribution() != null ? registerRequest.getRequireAttribution() : 1);
        copyright.setTraceCount(0L);

        int result = copyrightMapper.insert(copyright);
        ThrowUtils.throwIf(result <= 0, ErrorCode.OPERATION_ERROR, "版权登记失败");

        log.info("用户 {} 为图片 {} 申请版权登记成功，溯源码：{}", loginUser.getId(), pictureId, copyrightCode);
        return copyright.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateCopyright(CopyrightRegisterRequest updateRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(updateRequest == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = updateRequest.getPictureId();
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片ID不能为空");

        String copyrightOwner = updateRequest.getCopyrightOwner();
        ThrowUtils.throwIf(StringUtils.isBlank(copyrightOwner), ErrorCode.PARAMS_ERROR, "版权所有者姓名不能为空");

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 查询现有版权记录
        QueryWrapper<PictureCopyright> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pictureId", pictureId);
        PictureCopyright existCopyright = copyrightMapper.selectOne(queryWrapper);
        ThrowUtils.throwIf(existCopyright == null, ErrorCode.NOT_FOUND_ERROR, "该图片未登记版权");

        // 验证权限：只有版权所有者可以更新
        ThrowUtils.throwIf(!existCopyright.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能更新自己的版权信息");

        // 更新版权信息（保留溯源码和查询次数）
        existCopyright.setCopyrightOwner(copyrightOwner);
        existCopyright.setCopyrightDesc(updateRequest.getCopyrightDesc());
        existCopyright.setAllowCommercial(updateRequest.getAllowCommercial() != null ? updateRequest.getAllowCommercial() : 0);
        existCopyright.setRequireAttribution(updateRequest.getRequireAttribution() != null ? updateRequest.getRequireAttribution() : 1);

        int result = copyrightMapper.updateById(existCopyright);
        ThrowUtils.throwIf(result <= 0, ErrorCode.OPERATION_ERROR, "版权更新失败");

        log.info("用户 {} 更新图片 {} 的版权信息成功", loginUser.getId(), pictureId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CopyrightInfoVO traceCopyright(CopyrightTraceRequest traceRequest, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(traceRequest == null, ErrorCode.PARAMS_ERROR);
        String copyrightCode = traceRequest.getCopyrightCode();
        ThrowUtils.throwIf(StringUtils.isBlank(copyrightCode), ErrorCode.PARAMS_ERROR, "版权溯源码不能为空");

        // 查询版权信息 - 使用驼峰命名
        QueryWrapper<PictureCopyright> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("copyrightCode", copyrightCode);  // 使用驼峰命名
        PictureCopyright copyright = copyrightMapper.selectOne(queryWrapper);
        ThrowUtils.throwIf(copyright == null, ErrorCode.NOT_FOUND_ERROR, "未找到对应的版权信息");

        // 记录溯源查询
        PictureCopyrightTrace trace = new PictureCopyrightTrace();
        trace.setCopyrightId(copyright.getId());
        trace.setPictureId(copyright.getPictureId());
        trace.setCopyrightCode(copyrightCode);

        // 获取当前用户（可能未登录）
        try {
            User loginUser = userService.getLoginUser(request);
            if (loginUser != null) {
                trace.setTraceUserId(loginUser.getId());
            }
        } catch (Exception e) {
            // 未登录用户也可以查询
        }

        // 获取IP地址
        String ipAddr = ServletUtils.getClientIP(request);
        trace.setTraceIp(ipAddr);
        trace.setTraceTime(new Date());

        traceMapper.insert(trace);

        // 更新溯源查询次数
        copyright.setTraceCount(copyright.getTraceCount() + 1);
        copyrightMapper.updateById(copyright);

        // 构建返回对象
        CopyrightInfoVO vo = new CopyrightInfoVO();
        BeanUtils.copyProperties(copyright, vo);

        // 查询图片信息
        Picture picture = pictureMapper.selectById(copyright.getPictureId());
        if (picture != null) {
            vo.setPictureUrl(picture.getUrl());
            vo.setPictureName(picture.getName());
        }

        log.info("版权溯源查询：溯源码 {}，IP {}，查询次数 {}", copyrightCode, ipAddr, copyright.getTraceCount());
        return vo;
    }

    @Override
    public String generateCopyrightCode(Long pictureId, Long userId) {
        // 生成格式：CR-年份-图片ID后4位-随机8位
        String year = String.valueOf(new Date().getYear() + 1900);
        String pictureSuffix = String.format("%04d", pictureId % 10000);
        String randomPart = IdUtil.randomUUID().substring(0, 8).toUpperCase();
        return String.format("CR-%s-%s-%s", year, pictureSuffix, randomPart);
    }

    @Override
    public CopyrightInfoVO getCopyrightInfoByPictureId(Long pictureId) {
        ThrowUtils.throwIf(pictureId == null || pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片ID不能为空");

        // 使用驼峰命名
        QueryWrapper<PictureCopyright> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pictureId", pictureId);  // 使用驼峰命名
        PictureCopyright copyright = copyrightMapper.selectOne(queryWrapper);

        if (copyright == null) {
            return null;
        }

        CopyrightInfoVO vo = new CopyrightInfoVO();
        BeanUtils.copyProperties(copyright, vo);

        // 查询图片信息
        Picture picture = pictureMapper.selectById(pictureId);
        if (picture != null) {
            vo.setPictureUrl(picture.getUrl());
            vo.setPictureName(picture.getName());
        }

        return vo;
    }
}
