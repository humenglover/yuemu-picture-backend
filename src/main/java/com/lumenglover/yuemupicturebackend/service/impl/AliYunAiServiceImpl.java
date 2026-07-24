package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.service.AliYunAiService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AliYunAiServiceImpl implements AliYunAiService {

    @Value("${aliYunAi.apiKey}")
    private String apiKey;

    @Value("${ai.dashscope.embedding.model:tongyi-embedding-vision-flash}")
    private String modelName;

    @Value("${ai.dashscope.embedding.url:https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding}")
    private String apiUrl;

    @Override
    public List<Double> getImageEmbedding(String imageUrl) {
        JSONObject content = new JSONObject();
        content.set("image", imageUrl);
        return getEmbedding(content);
    }

    @Override
    public List<Double> getTextEmbedding(String text) {
        JSONObject content = new JSONObject();
        content.set("text", text);
        return getEmbedding(content);
    }

    /**
     * 发送 HTTP 请求调用原生大模型接口（不依赖 SDK 版本兼容）
     */
    private List<Double> getEmbedding(JSONObject content) {
        try {
            // 构造参数
            JSONObject input = new JSONObject();
            JSONArray contents = new JSONArray();
            contents.add(content);
            input.set("contents", contents);

            JSONObject param = new JSONObject();
            param.set("model", modelName);
            param.set("input", input);

            // 发起原生 HTTP POST 请求
            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(param.toString())
                    .execute();

            try {
                if (response.isOk()) {
                    JSONObject resJson = JSONUtil.parseObj(response.body());
                    JSONObject output = resJson.getJSONObject("output");
                    if (output != null) {
                        JSONArray embeddings = output.getJSONArray("embeddings");
                        if (CollUtil.isNotEmpty(embeddings)) {
                            JSONObject firstEmbedding = embeddings.getJSONObject(0);
                            JSONArray embeddingArray = firstEmbedding.getJSONArray("embedding");

                            List<Double> result = new ArrayList<>();
                            for (Object o : embeddingArray) {
                                result.add(((Number) o).doubleValue());
                            }
                            return result;
                        }
                    }
                }
                log.error("调用阿里云多模态大模型失败, HTTP 响应: {}", response.body());
            } finally {
                response.close();
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "大模型调用失败，请检查额度或网络");

        } catch (Exception e) {
            log.error("大模型 HTTP 请求异常", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "大模型 HTTP 调用异常: " + e.getMessage());
        }
    }
}
