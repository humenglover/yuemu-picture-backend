package com.lumenglover.yuemupicturebackend.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.model.dto.picture.BatchUploadProgress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量上传进度 WebSocket 处理器
 */
@Slf4j
@Component
public class BatchUploadWebSocketHandler extends TextWebSocketHandler {

    /**
     * 存储所有连接的会话
     * Key: userId, Value: WebSocketSession
     */
    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();

    /**
     * 连接建立时
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            SESSIONS.put(userId, session);
            log.info("批量上传WebSocket连接建立 | userId: {} | sessionId: {}", userId, session.getId());

            // 发送连接成功消息
            sendMessage(session, BatchUploadProgress.builder()
                    .stage("connected")
                    .stageDescription("WebSocket连接成功")
                    .build());
        } else {
            log.warn("WebSocket连接缺少userId参数，关闭连接");
            session.close();
        }
    }

    /**
     * 连接关闭时
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        if (userId != null) {
            SESSIONS.remove(userId);
            log.info("批量上传WebSocket连接关闭 | userId: {} | sessionId: {} | status: {}",
                    userId, session.getId(), status);
        }
    }

    /**
     * 处理接收到的消息（心跳等）
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("收到WebSocket消息 | sessionId: {} | message: {}", session.getId(), payload);

        // 处理心跳
        if ("ping".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    /**
     * 发生错误时
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = getUserIdFromSession(session);
        log.error("WebSocket传输错误 | userId: {} | sessionId: {}", userId, session.getId(), exception);

        if (session.isOpen()) {
            session.close();
        }
        if (userId != null) {
            SESSIONS.remove(userId);
        }
    }

    /**
     * 向指定用户发送进度更新
     */
    public void sendProgressToUser(String userId, BatchUploadProgress progress) {
        log.info("📡 尝试向用户推送进度 | userId: {} | stage: {} | 当前在线用户数: {} | 在线用户列表: {}",
                userId, progress.getStage(), SESSIONS.size(), SESSIONS.keySet());

        WebSocketSession session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            log.info("📡 找到用户会话，准备发送消息 | userId: {} | sessionId: {}", userId, session.getId());
            sendMessage(session, progress);
        } else {
            log.warn("⚠️ 用户 {} 的WebSocket会话不存在或已关闭，跳过进度推送 | 会话存在: {} | 会话打开: {}",
                    userId, session != null, session != null && session.isOpen());
        }
    }

    /**
     * 发送消息（线程安全）
     */
    private synchronized void sendMessage(WebSocketSession session, BatchUploadProgress progress) {
        try {
            if (session == null || !session.isOpen()) {
                log.warn("⚠️ WebSocket 会话不可用，跳过消息发送");
                return;
            }

            String json = JSONUtil.toJsonStr(progress);
            session.sendMessage(new TextMessage(json));
            log.debug("发送进度消息 | sessionId: {} | stage: {} | percentage: {}%",
                    session.getId(), progress.getStage(), progress.getPercentage());
        } catch (IOException e) {
            log.error("发送WebSocket消息失败 | sessionId: {}", session.getId(), e);
        } catch (Exception e) {
            log.error("发送WebSocket消息异常 | sessionId: {}", session.getId(), e);
        }
    }

    /**
     * 从会话中获取用户ID
     */
    private String getUserIdFromSession(WebSocketSession session) {
        try {
            String query = session.getUri().getQuery();
            if (query != null && query.contains("userId=")) {
                String[] params = query.split("&");
                for (String param : params) {
                    if (param.startsWith("userId=")) {
                        return param.substring("userId=".length());
                    }
                }
            }
        } catch (Exception e) {
            log.error("解析userId失败", e);
        }
        return null;
    }

    /**
     * 获取在线用户数
     */
    public int getOnlineCount() {
        return SESSIONS.size();
    }

    /**
     * 检查用户是否在线
     */
    public boolean isUserOnline(String userId) {
        WebSocketSession session = SESSIONS.get(userId);
        return session != null && session.isOpen();
    }
}
