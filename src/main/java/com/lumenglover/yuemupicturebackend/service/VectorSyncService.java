package com.lumenglover.yuemupicturebackend.service;

/**
 * 全量向量化同步服务
 */
public interface VectorSyncService {

    /**
     * 异步执行全量图片向 Qdrant 数据库的同步操作
     */
    void runFullSyncAsync();
}
