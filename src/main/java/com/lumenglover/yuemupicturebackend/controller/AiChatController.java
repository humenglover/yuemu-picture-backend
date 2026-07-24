package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.IDeepSeekService;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.*;
import cn.dev33.satoken.annotation.SaCheckRole;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckRole;
import java.util.List;
import cn.dev33.satoken.annotation.SaCheckRole;
import javax.servlet.http.HttpServletRequest;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.entity.AiChat;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.vo.AiChatVO;
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

@RestController
@RequestMapping("/deepseek")
public class AiChatController {

    @Resource
    private IDeepSeekService deepSeekService;

    @PostMapping("/send")
    @RateLimiter(key = "ai_chat_send", time = 60, count = 60, message = "AI聊天发送过于频繁，请稍后再试")
    public String send(String query, HttpServletRequest request) {
        return deepSeekService.generateResponse(query, request);
    }

    @PostMapping("/history")
    @RateLimiter(key = "ai_chat_history", time = 60, count = 60, message = "聊天历史查询过于频繁，请稍后再试")
    public Page<AiChatVO> getChatHistory(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return deepSeekService.getChatHistory(request, current, pageSize);
    }

    /**
     * 管理员获取所有聊天记录
     */
    @GetMapping("/admin/list")
    @SaCheckRole("admin")
    public BaseResponse<Page<AiChat>> listChatByPageAdmin(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String role) {
        Page<AiChat> page = deepSeekService.listChatByPageAdmin(current, pageSize, userId, role);
        return ResultUtils.success(page);
    }

    /**
     * 管理员批量删除聊天记录
     */
    @DeleteMapping("/admin/delete/batch")
    @SaCheckRole("admin")
    public BaseResponse<Boolean> batchDeleteChat(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = deepSeekService.removeBatchByIds(ids);
        return ResultUtils.success(result);
    }
}
