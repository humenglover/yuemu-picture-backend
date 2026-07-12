package com.lumenglover.yuemupicturebackend.manager.websocket;

import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.model.vo.AiTokenUsageVO;
import com.lumenglover.yuemupicturebackend.service.AiTokenRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI Token 用量 WebSocket 处理器
 */
@Slf4j
@Component
public class AiTokenWebSocketHandler extends TextWebSocketHandler {

    @Resource
    @Lazy
    private AiTokenRecordService aiTokenRecordService;

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
            log.info("AI Token WebSocket连接建立 | userId: {} | sessionId: {}", userId, session.getId());

            // 建立连接后立刻推送一次当前用量
            sendUsageToUser(userId);
        } else {
            log.warn("AI Token WebSocket连接缺少userId参数，关闭连接");
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
            log.info("AI Token WebSocket连接关闭 | userId: {} | sessionId: {} | status: {}",
                    userId, session.getId(), status);
        }
    }

    /**
     * 处理接收到的消息（心跳等）
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
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
        // EOFException 是客户端正常关闭连接（切页面/App后台化），不是真实错误，降级为 DEBUG 避免日志污染
        if (exception instanceof java.io.EOFException) {
            log.debug("AI Token WebSocket客户端断开连接 | userId: {} | sessionId: {}", userId, session.getId());
        } else {
            log.warn("AI Token WebSocket传输错误 | userId: {} | sessionId: {}", userId, session.getId(), exception);
        }

        if (session.isOpen()) {
            session.close();
        }
        if (userId != null) {
            SESSIONS.remove(userId);
        }
    }

    /**
     * 获取最新用量并向指定用户发送
     */
    public void sendUsageToUser(String userId) {
        WebSocketSession session = SESSIONS.get(userId);
        if (session != null && session.isOpen()) {
            try {
                AiTokenUsageVO tokenUsage = aiTokenRecordService.getTokenUsage(Long.valueOf(userId));
                if (tokenUsage != null) {
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("type", "ai_token_usage_response");
                    response.put("data", tokenUsage);
                    sendMessage(session, response);
                }
            } catch (Exception e) {
                log.error("获取并发送AI Token用量失败 | userId: {}", userId, e);
            }
        }
    }

    /**
     * 发送消息（线程安全）
     */
    private synchronized void sendMessage(WebSocketSession session, Object message) {
        try {
            if (session == null || !session.isOpen()) {
                return;
            }
            String json = JSONUtil.toJsonStr(message);
            session.sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.error("发送AI Token WebSocket消息失败 | sessionId: {}", session.getId(), e);
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
}
