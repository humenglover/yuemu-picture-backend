package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.mapper.MessageBoardMapper;
import com.lumenglover.yuemupicturebackend.model.entity.MessageBoard;
import com.lumenglover.yuemupicturebackend.service.MessageBoardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import com.lumenglover.yuemupicturebackend.manager.TextModerationManager;
import com.lumenglover.yuemupicturebackend.model.entity.SystemNotify;
import com.lumenglover.yuemupicturebackend.service.SystemNotifyService;
import com.lumenglover.yuemupicturebackend.manager.websocket.MessageWebSocketHandler;
import org.springframework.context.annotation.Lazy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 祝福板服务实现类
 */
@Service
public class MessageBoardServiceImpl extends ServiceImpl<MessageBoardMapper, MessageBoard>
        implements MessageBoardService {

    private static final Logger log = LoggerFactory.getLogger(MessageBoardServiceImpl.class);

    @Resource
    private TextModerationManager textModerationManager;

    @Resource
    private SystemNotifyService systemNotifyService;

    @Resource
    @Lazy
    private MessageWebSocketHandler messageWebSocketHandler;

    @Resource
    private com.lumenglover.yuemupicturebackend.service.LoveBoardService loveBoardService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addMessage(MessageBoard messageBoard) {
        // 验证必填字段
        if (messageBoard.getOwnerId() == null) {
            throw new RuntimeException("祝福板主人ID不能为空");
        }
        // 设置默认值
        if (messageBoard.getStatus() == null) {
            messageBoard.setStatus(1);
        }
        if (messageBoard.getLikeCount() == null) {
            messageBoard.setLikeCount(0);
        }
        boolean res = save(messageBoard);
        if (res && messageBoard.getContent() != null && !messageBoard.getContent().isEmpty()) {
            final Long msgId = messageBoard.getId();
            final Long senderId = messageBoard.getUserId();
            final String originalContent = messageBoard.getContent();
            textModerationManager.moderateTextAsync(messageBoard.getContent(), "accurate", text -> {
                MessageBoard msg = this.getById(msgId);
                if (msg != null) {
                    this.update().set("content", text).eq("id", msgId).update();
                    log.info("祝福板留言包含违规内容已被屏蔽: id={}", msgId);

                    try {
                        SystemNotify systemNotify = new SystemNotify();
                        systemNotify.setTitle("留言违规提醒");
                        systemNotify.setNotifyType("MESSAGE_BOARD_REJECTED");
                        systemNotify.setReceiverType("SPECIFIC_USER");
                        if (senderId != null) {
                            systemNotify.setReceiverId(String.valueOf(senderId));
                        } else {
                            systemNotify.setReceiverId(String.valueOf(msg.getOwnerId()));
                        }
                        systemNotify.setSenderId("system");
                        systemNotify.setSenderType("SYSTEM");
                        systemNotify.setIsEnabled(1);
                        systemNotify.setIsGlobal(0);
                        systemNotify.setReadStatus(0);

                        systemNotify.setContent(String.format("您的留言因包含敏感词已被自动脱敏，原始文本：【%s】", originalContent));
                        systemNotify.setNotifyIcon("reject");
                        systemNotify.setRelatedBizType("MESSAGE_BOARD");
                        systemNotify.setRelatedBizId(String.valueOf(msgId));

                        systemNotifyService.addSystemNotify(systemNotify);

                        if (messageWebSocketHandler != null && senderId != null) {
                            messageWebSocketHandler.sendUnreadCountToUser(String.valueOf(senderId));
                        }
                    } catch (Exception e) {
                        log.error("发送留言违规通知失败", e);
                    }
                }
            });
        }
        return res;
    }

    @Override
    public Page<MessageBoard> listMessagesByPage(long current, long size, Long ownerId) {
        QueryWrapper<MessageBoard> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("ownerId", ownerId)
                .eq("status", 1)
                .orderByDesc("createTime");
        return page(new Page<>(current, size), queryWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean likeMessage(Long id) {
        return update().setSql("likeCount = likeCount + 1")
                .eq("id", id)
                .update();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateMessageStatus(Long id, Integer status) {
        MessageBoard messageBoard = new MessageBoard();
        messageBoard.setId(id);
        messageBoard.setStatus(status);
        return updateById(messageBoard);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteMessage(Long id, Long ownerId) {
        // 验证是否是祝福板主人或伴侣
        MessageBoard messageBoard = getById(id);
        if (messageBoard == null) {
            throw new RuntimeException("祝福不存在");
        }
        // 检查是否有权限（创建者或伴侣）
        if (!loveBoardService.hasLoveBoardPermission(messageBoard.getOwnerId(), ownerId)) {
            throw new RuntimeException("无权删除该祝福");
        }
        return removeById(id);
    }
}
