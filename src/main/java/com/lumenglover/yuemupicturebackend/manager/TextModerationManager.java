package com.lumenglover.yuemupicturebackend.manager;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.config.RagConfig;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * 文本审核管理器
 */
@Component
@Slf4j
public class TextModerationManager {

    @Resource
    private RagConfig ragConfig;

    /**
     * 同步审核文本
     *
     * @param text 待审核文本
     * @param mode 模式: fast, accurate, strict
     */
    public String moderateTextSync(String text, String mode) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        if (!ragConfig.getPythonService().getEnabled()) {
            return text;
        }

        try {
            String baseUrl = ragConfig.getPythonService().getBaseUrl();
            String endpoint = ragConfig.getPythonService().getAi() != null
                    && ragConfig.getPythonService().getAi().getCommentModerationEndpoint() != null
                    ? ragConfig.getPythonService().getAi().getCommentModerationEndpoint()
                    : "/api/comment/moderation";

            String url = baseUrl + endpoint;

            JSONObject requestBody = new JSONObject();
            requestBody.set("comments", Collections.singletonList(text));
            requestBody.set("mode", mode);

            HttpResponse response = HttpRequest.post(url)
                    .body(requestBody.toString())
                    .timeout(15000) // 增加超时时间到 15s 以适配低内存环境
                    .execute();

            try {
                if (response.isOk()) {
                    JSONObject resJson = JSONUtil.parseObj(response.body());
                    JSONObject results = resJson.getJSONObject("results");
                    if (results != null && results.containsKey(text)) {
                        JSONObject result = results.getJSONObject(text);
                        Integer label = result.getInt("label");
                        if (label != null && label == 1) {
                            String filteredText = result.getStr("filtered_text");
                            if (filteredText != null) {
                                log.warn("文本审核不通过，包含违规词，已自动替换: {}", text);
                                return filteredText;
                            }
                        }
                    }
                } else {
                    log.error("文本审核调用失败, status: {}, body: {}", response.getStatus(), response.body());
                }
            } finally {
                response.close();
            }
        } catch (Exception e) {
            log.error("文本审核服务调用异常", e);
        }
        return text;
    }

    /**
     * 异步审核文本
     *
     * @param text        待审核文本
     * @param mode        模式: fast, accurate, strict
     * @param onViolation 如果违规则执行的回调
     */
    public void moderateTextAsync(String text, String mode, Consumer<String> onViolation) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        if (!ragConfig.getPythonService().getEnabled()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String baseUrl = ragConfig.getPythonService().getBaseUrl();
                String endpoint = ragConfig.getPythonService().getAi() != null
                        && ragConfig.getPythonService().getAi().getCommentModerationEndpoint() != null
                        ? ragConfig.getPythonService().getAi().getCommentModerationEndpoint()
                        : "/api/comment/moderation";

                String url = baseUrl + endpoint;

                JSONObject requestBody = new JSONObject();
                requestBody.set("comments", Collections.singletonList(text));
                requestBody.set("mode", mode);

                HttpResponse response = HttpRequest.post(url)
                        .body(requestBody.toString())
                        .timeout(10000) // 10秒超时足够
                        .execute();

                try {
                    if (response.isOk()) {
                        JSONObject resJson = JSONUtil.parseObj(response.body());
                        JSONObject results = resJson.getJSONObject("results");
                        if (results != null && results.containsKey(text)) {
                            JSONObject result = results.getJSONObject(text);
                            Integer label = result.getInt("label");
                            if (label != null && label == 1) {
                                String filteredText = result.getStr("filtered_text");
                                log.warn("文本审核不通过，包含违规词，已自动替换: {}", text);
                                if (onViolation != null) {
                                    onViolation.accept(filteredText != null ? filteredText : text);
                                }
                            }
                        }
                    } else {
                        log.error("文本审核调用失败, status: {}, body: {}", response.getStatus(), response.body());
                    }
                } finally {
                    response.close();
                }
            } catch (Exception e) {
                log.error("文本审核服务调用异常", e);
            }
        });
    }
}
