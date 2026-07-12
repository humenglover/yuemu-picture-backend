package com.lumenglover.yuemupicturebackend.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.function.Consumer;

import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse;

/**
 * RAG接口
 */
@Service
public interface RagService {

    /**
     * RAG同步问答
     */
    PythonRagResponse chat(Long userId, Long sessionId, String question, String saToken);

    /**
     * RAG流式问答
     */
    void chatStream(Long userId, Long sessionId, String question, String saToken, String model, Consumer<Map<String, Object>> resultConsumer, Consumer<Integer> onComplete);

    /**
     * 清除用户对话上下文
     */
    void clearUserContext(Long userId);

    /**
     * 清除指定会话的上下文
     */
    void clearSessionContext(Long sessionId);

    /**
     * 搜索长期记忆
     */
    String searchLongTermMemory(Long userId, String keyword);

    /**
     * 异步检查并生成会话摘要
     * @param sessionId
     * @param userId
     */
    void checkAndGenerateSummaryAsync(Long sessionId, Long userId);
}
