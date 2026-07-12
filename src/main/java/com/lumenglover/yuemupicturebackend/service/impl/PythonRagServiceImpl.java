package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.config.RagConfig;
import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagRequest;
import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse;
import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonPureLLMRequest;
import com.lumenglover.yuemupicturebackend.service.PythonRagService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
@Service
public class PythonRagServiceImpl implements PythonRagService {

    @Autowired
    private RagConfig ragConfig;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        // 初始化WebClient,使用配置的基础URL
        String baseUrl = ragConfig.getPythonService().getBaseUrl();

        // 增大 WebClient 默认缓冲区大小为 10MB，防止长文本合成音频流超出 256KB 限制报错
        org.springframework.web.reactive.function.client.ExchangeStrategies strategies =
            org.springframework.web.reactive.function.client.ExchangeStrategies.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(strategies)
                .build();
        log.info("WebClient initialized with base URL: {}", baseUrl);
    }

    @Override
    public PythonRagResponse callPythonRagSync(String question, List<Map<String, String>> history, String sessionId, String ltmContext, String saToken, String userPersona) {
        if (StrUtil.isBlank(question)) {
            log.warn("Python RAG同步调用 - 问题为空");
            return PythonRagResponse.builder()
                    .answer("")
                    .sessionId(sessionId)
                    .build();
        }

        try {
            PythonRagRequest request = PythonRagRequest.builder()
                    .question(question)
                    .history(history != null ? history : CollUtil.newArrayList())
                    .session_id(sessionId)
                    .longTermMemory(ltmContext)
                    .sa_token(saToken)
                    .user_persona(userPersona)
                    .build();

            String syncEndpoint = ragConfig.getPythonService().getSyncEndpoint();

            log.info("Python RAG同步调用 - 请求 | URL: {} | 问题: {} | LTM长度: {}",
                    syncEndpoint, question, ltmContext != null ? ltmContext.length() : 0);

            // 使用WebClient进行同步调用，按String接收以拆解ResponseWrapper
            String responseStr = webClient.post()
                    .uri(syncEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 阻塞等待响应

            String answer = "";
            Integer totalTokens = 0;
            if (StrUtil.isNotBlank(responseStr)) {
                cn.hutool.json.JSONObject jsonObject = cn.hutool.json.JSONUtil.parseObj(responseStr);
                int code = jsonObject.getInt("code", 500);
                if (code == 200) {
                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                    if (dataObj != null) {
                        answer = dataObj.getStr("answer", "");
                        totalTokens = dataObj.getInt("total_tokens", 0);
                    }
                }
            }

            log.info("Python RAG同步调用 - 响应 | 响应长度: {}", answer.length());

            return PythonRagResponse.builder()
                    .answer(answer)
                    .sessionId(sessionId)
                    .totalTokens(totalTokens)
                    .build();

        } catch (Exception e) {
            log.error("Python RAG同步调用异常", e);
            return PythonRagResponse.builder()
                    .answer("Python RAG服务调用异常: " + e.getMessage())
                    .sessionId(sessionId)
                    .build();
        }
    }

    @Override
    public void callPythonRagStream(String question, List<Map<String, String>> history, String sessionId, String ltmContext, String saToken, String userPersona, String model, Consumer<Map<String, Object>> resultConsumer, Runnable onComplete) {
        if (StrUtil.isBlank(question)) {
            log.warn("Python RAG流式调用 - 问题为空");
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("type", "token");
            resultMap.put("content", "");
            resultConsumer.accept(resultMap);
            onComplete.run();
            return;
        }

        try {
            PythonRagRequest request = PythonRagRequest.builder()
                    .question(question)
                    .history(history != null ? history : CollUtil.newArrayList())
                    .session_id(sessionId)
                    .longTermMemory(ltmContext)
                    .sa_token(saToken)
                    .user_persona(userPersona)
                    .model(model != null ? model.toLowerCase() : "qwen3.5-flash")
                    .build();

            String streamEndpoint = ragConfig.getPythonService().getStreamEndpoint();

            log.info("Python RAG流式调用 - 请求 | URL: {} | 问题: {}", streamEndpoint, question);

            // 使用WebClient进行SSE流式调用
            Flux<ServerSentEvent<String>> eventStream = webClient.post()
                    .uri(streamEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .accept(MediaType.TEXT_EVENT_STREAM)
                    .retrieve()
                    .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {});

            final Disposable[] subscriptionHolder = new Disposable[1];
            // 订阅SSE事件流
            subscriptionHolder[0] = eventStream.subscribe(
                    event -> {
                        // 处理每个SSE事件
                        String eventName = event.event();
                        String data = event.data();

                        log.debug("接收到SSE事件 | 类型: {} | 数据: {}", eventName, data);

                        if (data != null) {
                            try {
                                cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(data);
                                String eventType = jsonObject.getStr("event");

                                if ("chunk".equals(eventType)) {
                                    // 提取chunk内容：支持嵌套多层的情况
                                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                                    if (dataObj != null) {
                                        // 兼容处理：有些结构是 data -> chunk，有些是 data -> data -> chunk
                                        String chunk = dataObj.getStr("chunk");
                                        if (chunk == null) {
                                            cn.hutool.json.JSONObject innerData = dataObj.getJSONObject("data");
                                            if (innerData != null) {
                                                chunk = innerData.getStr("chunk");
                                            }
                                        }

                                        if (chunk != null) {
                                            log.debug("【PythonRAG】解析到Chunk | 长度: {} | 内容预览: [{}]",
                                                    chunk.length(), chunk.length() > 20 ? chunk.substring(0, 20) + "..." : chunk);
                                            Map<String, Object> resultMap = new HashMap<>();
                                            resultMap.put("type", "token");
                                            resultMap.put("content", chunk);
                                            resultConsumer.accept(resultMap);
                                        }
                                    }
                                } else if ("status".equals(eventType)) {
                                    // 提取状态信息：Python 端结构通常是 data -> data -> status
                                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                                    if (dataObj != null) {
                                        // 遍历多层 data 结构查找 status
                                        String status = dataObj.getStr("status");
                                        if (status == null && dataObj.containsKey("data")) {
                                            cn.hutool.json.JSONObject innerData = dataObj.getJSONObject("data");
                                            if (innerData != null) {
                                                status = innerData.getStr("status");
                                            }
                                        }

                                        if (status != null) {
                                            log.info("【PythonRAG】解析到状态更新: {}", status);
                                            Map<String, Object> resultMap = new HashMap<>();
                                            resultMap.put("type", "status");
                                            resultMap.put("content", status);
                                            resultConsumer.accept(resultMap);
                                        }
                                    }
                                } else if ("done".equals(eventType) || "end".equals(eventType)) {
                                    log.info("【PythonRAG】接收到结束事件 | 数据摘要: {}", data.length() > 100 ? data.substring(0, 100) + "..." : data);

                                    // 关键安全网：某些情况下 chunk 可能因为网络/并发丢失，
                                    // python 端的 done 事件通常包含完整答案字段。我们需要利用它来兜底。
                                    try {
                                        cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                                        if (dataObj != null) {
                                            if (dataObj.containsKey("answer")) {
                                                String fullAnswerFromDone = dataObj.getStr("answer");
                                                if (StrUtil.isNotBlank(fullAnswerFromDone)) {
                                                    log.info("【PythonRAG】从Done事件提取到完整答案，总长度: {}", fullAnswerFromDone.length());
                                                }
                                            }
                                            if (dataObj.containsKey("total_tokens")) {
                                                Integer totalTokens = dataObj.getInt("total_tokens", 0);
                                                Map<String, Object> tokenUsageMap = new HashMap<>();
                                                tokenUsageMap.put("type", "total_tokens");
                                                tokenUsageMap.put("content", totalTokens);
                                                resultConsumer.accept(tokenUsageMap);
                                            }
                                        }
                                    } catch (Exception doneEx) {
                                        log.warn("【PythonRAG】解析Done事件详情失败", doneEx);
                                    }
                                } else if ("error".equals(eventType)) {
                                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                                    if (dataObj != null) {
                                        String errorMsg = dataObj.getStr("msg");
                                        if (StrUtil.isNotBlank(errorMsg)) {
                                            Map<String, Object> resultMap = new HashMap<>();
                                            resultMap.put("type", "token");
                                            resultMap.put("content", "错误: " + errorMsg);
                                            resultConsumer.accept(resultMap);
                                        }
                                    }
                                    log.error("接收到错误事件");
                                } else if ("start".equals(eventType)) {
                                    log.info("接收到开始事件");
                                }
                            } catch (Exception parseEx) {
                                log.warn("解析SSE数据失败 | 数据: {}", data, parseEx);
                            }
                        }
                    },
                    error -> {
                        // 错误处理
                        log.error("Python RAG流式调用异常", error);
                        Map<String, Object> resultMap = new HashMap<>();
                        resultMap.put("type", "token");
                        resultMap.put("content", "Python RAG服务调用异常: " + error.getMessage());
                        resultConsumer.accept(resultMap);
                        onComplete.run();
                        // 主动释放订阅，防止内存泄漏
                        if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                            subscriptionHolder[0].dispose();
                        }
                    },
                    () -> {
                        // 完成处理
                        log.info("Python RAG流式调用 - 数据读取完成");
                        onComplete.run();
                        // 主动释放订阅，防止闭包引用悬挂内存
                        if (subscriptionHolder[0] != null && !subscriptionHolder[0].isDisposed()) {
                            subscriptionHolder[0].dispose();
                        }
                    }
            );

        } catch (Exception e) {
            log.error("Python RAG流式调用异常", e);
            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("type", "token");
            resultMap.put("content", "Python RAG服务调用异常: " + e.getMessage());
            resultConsumer.accept(resultMap);
            onComplete.run();
        }
    }

    @Override
    public PythonRagResponse callPythonSummarize(String text) {
        if (StrUtil.isBlank(text)) {
            return PythonRagResponse.builder().answer("").build();
        }
        try {
            PythonRagRequest request = PythonRagRequest.builder()
                    .question(text)
                    .build();

            String summarizeEndpoint = ragConfig.getPythonService().getSummarizeEndpoint();
            log.info("Python 摘要调用 - 请求 | URL: {} | 文本长度: {}", summarizeEndpoint, text.length());

            String responseStr = webClient.post()
                    .uri(summarizeEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String answer = "";
            if (StrUtil.isNotBlank(responseStr)) {
                cn.hutool.json.JSONObject jsonObject = cn.hutool.json.JSONUtil.parseObj(responseStr);
                int code = jsonObject.getInt("code", 500);
                if (code == 200) {
                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                    if (dataObj != null) {
                        answer = dataObj.getStr("answer", "");
                    }
                }
            }

            log.info("Python 摘要调用 - 响应 | 结果长度: {}", answer.length());
            return PythonRagResponse.builder().answer(answer).build();
        } catch (Exception e) {
            log.error("Python 摘要调用异常", e);
            return PythonRagResponse.builder().answer("摘要服务异常: " + e.getMessage()).build();
        }
    }

    @Override
    public byte[] generateTts(String text, String voiceType) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        if (StrUtil.isBlank(voiceType)) {
            voiceType = "female_gentle";
        }
        try {
            String ttsEndpoint = ragConfig.getPythonService().getTtsEndpoint();
            // 使用 UriBuilder 变量替换防止双重编码
            String url = ttsEndpoint + "?text={text}&voice_type={voiceType}";

            log.info("Python TTS调用 - 请求 | URL: {} | 文本长度: {}", ttsEndpoint, text.length());

            byte[] audioBytes = webClient.get()
                    .uri(url, text, voiceType)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            log.info("Python TTS调用 - 响应 | 结果大小: {} bytes", audioBytes != null ? audioBytes.length : 0);
            return audioBytes;
        } catch (Exception e) {
            log.error("Python TTS调用异常", e);
            return null;
        }
    }

    @Override
    public PythonRagResponse callPythonPureLLM(String prompt, String model, Double temperature) {
        if (StrUtil.isBlank(prompt)) {
            log.warn("Python 纯LLM调用 - 提示词为空");
            return PythonRagResponse.builder().answer("").build();
        }

        try {
            PythonPureLLMRequest request = PythonPureLLMRequest.builder()
                    .prompt(prompt)
                    .model(model)
                    .temperature(temperature)
                    .build();

            log.info("Python 纯LLM调用 - 请求 | prompt 长度: {}", prompt.length());

            String pureChatEndpoint = ragConfig.getPythonService().getAi().getPureChatEndpoint();

            String responseStr = webClient.post()
                    .uri(pureChatEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String answer = "";
            if (StrUtil.isNotBlank(responseStr)) {
                cn.hutool.json.JSONObject jsonObject = cn.hutool.json.JSONUtil.parseObj(responseStr);
                int code = jsonObject.getInt("code", 500);
                if (code == 200) {
                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                    if (dataObj != null) {
                        answer = dataObj.getStr("answer", "");
                    }
                }
            }

            log.info("Python 纯LLM调用 - 响应 | 结果长度: {}", answer.length());
            return PythonRagResponse.builder().answer(answer).build();
        } catch (Exception e) {
            log.error("Python 纯LLM调用异常", e);
            return PythonRagResponse.builder().answer("纯AI对话服务异常: " + e.getMessage()).build();
        }
    }

    @Override
    public String getAiImageKeywords(String imageUrl) {
        if (StrUtil.isBlank(imageUrl)) {
            log.warn("Python 识图关键词提取 - 图片URL为空");
            return "";
        }

        try {
            Map<String, Object> request = new HashMap<>();
            request.put("image_url", imageUrl);

            log.info("Python 识图关键词提取 - 请求 | URL: {}", imageUrl);

            String endpoint = ragConfig.getPythonService().getAi().getImageKeywordsEndpoint();

            String responseStr = webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            String keywords = "";
            if (StrUtil.isNotBlank(responseStr)) {
                cn.hutool.json.JSONObject jsonObject = cn.hutool.json.JSONUtil.parseObj(responseStr);
                int code = jsonObject.getInt("code", 500);
                if (code == 200) {
                    cn.hutool.json.JSONObject dataObj = jsonObject.getJSONObject("data");
                    if (dataObj != null) {
                        keywords = dataObj.getStr("keywords", "");
                    }
                }
            }

            log.info("Python 识图关键词提取 - 响应 | 结果: {}", keywords);
            return keywords.trim();
        } catch (Exception e) {
            log.error("Python 识图关键词提取异常", e);
            return "";
        }
    }
}
