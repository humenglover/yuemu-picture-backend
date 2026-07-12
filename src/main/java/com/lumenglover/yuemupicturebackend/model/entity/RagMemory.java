package com.lumenglover.yuemupicturebackend.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/**
 * Meilisearch 存储的超长记忆索引模型
 */
@Data
public class RagMemory implements Serializable {

    private String id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 会话ID
     */
    private Long sessionId;

    /**
     * 记忆内容（摘要或关键对话片段）
     */
    private String content;

    /**
     * 消息类型 0-摘要 1-对话片段
     */
    private Integer memoryType;

    /**
     * 摘要层级：0-基础摘要，1-超级摘要
     */
    private Integer summaryLevel;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
