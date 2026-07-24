package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.dto.chatmessage.ChatMessageAdminRequest;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.dto.chatmessage.ChatMessageBatchRequest;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.entity.ChatMessage;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.ChatMessageService;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.UserService;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.transaction.annotation.Transactional;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.*;

import cn.dev33.satoken.annotation.SaCheckRole;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckRole;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/chat")
@Slf4j
public class ChatMessageController {

    @Resource
    private ChatMessageService chatMessageService;

    @Resource
    private UserService userService;

    /**
     * 分页获取聊天消息列表（仅管理员可用）
     */
    @PostMapping("/list/page/admin")
    @SaCheckRole("admin")
    @RateLimiter(key = "chat_message_list", time = 60, count = 60, message = "聊天消息列表查询过于频繁，请稍后再试")
    public BaseResponse<Page<ChatMessage>> listMessagesByPage(@RequestBody ChatMessageAdminRequest chatMessageAdminRequest) {
        long current = chatMessageAdminRequest.getCurrent();
        long size = chatMessageAdminRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 50, ErrorCode.PARAMS_ERROR);
        Page<ChatMessage> messagesPage = chatMessageService.page(new Page<>(current, size),
                chatMessageService.getAdminQueryWrapper(chatMessageAdminRequest));
        return ResultUtils.success(messagesPage);
    }

    /**
     * 根据 id 获取聊天消息（仅管理员可用）
     */
    @GetMapping("/get")
    @SaCheckRole("admin")
    @RateLimiter(key = "chat_message_get", time = 60, count = 60, message = "聊天消息详情查询过于频繁，请稍后再试")
    public BaseResponse<ChatMessage> getMessageById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        ChatMessage message = chatMessageService.getById(id);
        ThrowUtils.throwIf(message == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(message);
    }

    /**
     * 更新聊天消息（仅管理员可用）
     */
    @PostMapping("/update")
    @SaCheckRole("admin")
    @Transactional(rollbackFor = Exception.class)
    @RateLimiter(key = "chat_message_update", time = 60, count = 60, message = "聊天消息更新过于频繁，请稍后再试")
    public BaseResponse<Boolean> updateMessage(@RequestBody ChatMessage message) {
        ThrowUtils.throwIf(message == null || message.getId() == null, ErrorCode.PARAMS_ERROR);
        boolean result = chatMessageService.updateById(message);
        return ResultUtils.success(result);
    }

    /**
     * 批量操作聊天消息（仅管理员可用）
     */
    @PostMapping("/batch")
    @SaCheckRole("admin")
    @RateLimiter(key = "chat_message_batch", time = 60, count = 60, message = "聊天消息批量操作过于频繁，请稍后再试")
    public BaseResponse<Boolean> batchOperationMessages(@RequestBody ChatMessageBatchRequest chatMessageBatchRequest,
                                                        HttpServletRequest request) {
        ThrowUtils.throwIf(chatMessageBatchRequest == null
                        || chatMessageBatchRequest.getIds() == null
                        || chatMessageBatchRequest.getIds().isEmpty(),
                ErrorCode.PARAMS_ERROR);

        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(!userService.isAdmin(loginUser), ErrorCode.NO_AUTH_ERROR);

        boolean result = chatMessageService.batchOperationMessages(chatMessageBatchRequest);
        return ResultUtils.success(result);
    }
}
