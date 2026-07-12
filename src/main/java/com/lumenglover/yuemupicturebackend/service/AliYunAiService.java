package com.lumenglover.yuemupicturebackend.service;

import java.util.List;

/**
 * 阿里云 AI 大模型服务接口
 */
public interface AliYunAiService {

    /**
     * 调用多模态大模型获取图片的 Embedding 向量
     * @param imageUrl 图片公网访问链接
     * @return 1024 维的 Float 向量列表
     */
    List<Double> getImageEmbedding(String imageUrl);

    /**
     * 调用多模态大模型获取文本的 Embedding 向量（用于文本搜图）
     * @param text 搜索文本
     * @return 1024 维的 Float 向量列表
     */
    List<Double> getTextEmbedding(String text);
}
