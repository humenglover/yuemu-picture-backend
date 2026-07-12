package com.lumenglover.yuemupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.DeleteRequest;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.model.entity.AiResource;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.service.AiResourceService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * AI资源库接口
 */
@RestController
@RequestMapping("/ai_resource")
@Slf4j
public class AiResourceController {

    @Resource
    private AiResourceService aiResourceService;

    @Resource
    private UserService userService;

    /**
     * 删除AI资源
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteAiResource(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() == null) {
            return (BaseResponse<Boolean>) ResultUtils.error(400, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        AiResource aiResource = aiResourceService.getById(deleteRequest.getId());
        if (aiResource == null) {
            return (BaseResponse<Boolean>) ResultUtils.error(404, "资源不存在");
        }
        if (!aiResource.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            return (BaseResponse<Boolean>) ResultUtils.error(403, "无权限");
        }
        boolean b = aiResourceService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 分页获取当前用户的AI资源
     */
    @GetMapping("/my/page")
    public BaseResponse<Page<AiResource>> listMyAiResourceByPage(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String resourceType,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<AiResource> page = new Page<>(current, pageSize);
        Page<AiResource> aiResourcePage = aiResourceService.lambdaQuery()
                .eq(AiResource::getUserId, loginUser.getId())
                .eq(resourceType != null, AiResource::getResourceType, resourceType)
                .orderByDesc(AiResource::getCreateTime)
                .page(page);
        return ResultUtils.success(aiResourcePage);
    }

    /**
     * 新增AI资源（手动补充）
     */
    @PostMapping("/add")
    public BaseResponse<Long> addAiResource(@RequestBody AiResource aiResource, HttpServletRequest request) {
        if (aiResource == null) {
            return (BaseResponse<Long>) ResultUtils.error(400, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        aiResource.setUserId(loginUser.getId());
        boolean b = aiResourceService.save(aiResource);
        return ResultUtils.success(aiResource.getId());
    }

    /**
     * 更新AI资源
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateAiResource(@RequestBody AiResource aiResource, HttpServletRequest request) {
        if (aiResource == null || aiResource.getId() == null) {
            return (BaseResponse<Boolean>) ResultUtils.error(400, "参数错误");
        }
        User loginUser = userService.getLoginUser(request);
        AiResource oldResource = aiResourceService.getById(aiResource.getId());
        if (oldResource == null) {
            return (BaseResponse<Boolean>) ResultUtils.error(404, "资源不存在");
        }
        if (!oldResource.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            return (BaseResponse<Boolean>) ResultUtils.error(403, "无权限");
        }
        boolean b = aiResourceService.updateById(aiResource);
        return ResultUtils.success(b);
    }
}
