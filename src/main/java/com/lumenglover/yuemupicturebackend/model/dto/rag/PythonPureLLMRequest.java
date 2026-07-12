package com.lumenglover.yuemupicturebackend.model.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Python 纯LLM请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PythonPureLLMRequest {

    /**
     * 提示词/文本内容
     */
    private String prompt;

    /**
     * 所选模型（可选）
     */
    private String model;

    /**
     * 温度参数（可选）
     */
    private Double temperature;
}
