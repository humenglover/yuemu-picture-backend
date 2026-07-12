package com.lumenglover.yuemupicturebackend.service;

import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Python RAG服务接口
 */
public interface PythonRagService {

    /**
     * 调用Python RAG同步接口
     *
     * @param question      问题
     * @param history       历史对话
     * @param sessionId     会话ID
     * @param ltmContext    超长记忆上下文
     * @param saToken       当前登录用户的 sa-token
     * @param userPersona   用户画像
     * @return Python RAG响应
     */
    PythonRagResponse callPythonRagSync(String question, List<Map<String, String>> history, String sessionId, String ltmContext, String saToken, String userPersona);

    /**
     * 调用Python RAG流式接口
     *
     * @param question      问题
     * @param history       历史对话
     * @param sessionId     会话ID
     * @param ltmContext    超长记忆上下文
     * @param saToken       当前登录用户的 sa-token
     * @param userPersona   用户画像
     * @param resultConsumer 负载消费者，Map中 type 区分 token 或 status
     * @param onComplete    完成回调
     */
    void callPythonRagStream(String question, List<Map<String, String>> history, String sessionId, String ltmContext, String saToken, String userPersona, String model, Consumer<Map<String, Object>> resultConsumer, Runnable onComplete);

    /**
     * 调用专用摘要生成接口（绕过 RAG 约束）
     *
     * @param text 待总结的原始文本
     * @return 摘要结果
     */
    PythonRagResponse callPythonSummarize(String text);

    /**
     * 生成 TTS 语音
     *
     * @param text 文本
     * @param voiceType 语音类型，例如 "female_gentle"
     * @return 音频字节流
     */
    byte[] generateTts(String text, String voiceType);

    /**
     * 调用Python纯LLM接口（无RAG，无上下文，无泄漏风险）
     *
     * @param prompt       提示词/文本
     * @param model        模型名称（可选）
     * @param temperature  采样温度（可选）
     * @return Python RAG 统一响应格式
     */
    PythonRagResponse callPythonPureLLM(String prompt, String model, Double temperature);

    /**
     * 获取AI识图关键词
     *
     * @param imageUrl 图片URL
     * @return 以空格分隔的关键词字符串
     */
    String getAiImageKeywords(String imageUrl);
}
