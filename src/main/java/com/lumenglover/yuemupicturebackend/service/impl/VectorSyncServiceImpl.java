package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.util.concurrent.RateLimiter;
import com.lumenglover.yuemupicturebackend.controller.VectorSyncController;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.service.AliYunAiService;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import com.lumenglover.yuemupicturebackend.service.VectorSyncService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static io.qdrant.client.ValueFactory.value;

@Slf4j
@Service
public class VectorSyncServiceImpl implements VectorSyncService {

    @Resource
    private PictureService pictureService;

    @Resource
    private AliYunAiService aliYunAiService;

    @Resource
    private QdrantClient qdrantClient;

    @Value("${qdrant.collection-name:yuemu_picture}")
    private String collectionName;

    @Async
    @Override
    public void runFullSyncAsync() {
        log.info("开始执行全局向量化跑批任务...");
        try {
            // 1. 查询全部未删除的图片
            // 因为目前的图片数量大概在 5000 左右，可以直接查出所需字段以节省内存。
            // 随着数据量变大（如十万级以上），建议改为 MyBatis-Plus 分页查询。
            QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("isDelete", 0);
            queryWrapper.select("id", "url", "userId", "spaceId");

            List<Picture> pictureList = pictureService.list(queryWrapper);
            if (pictureList == null || pictureList.isEmpty()) {
                log.info("没有需要处理的图片数据");
                return;
            }

            // 1.5 增量同步核心逻辑：查询 Qdrant，剔除已存在的记录
            try {
                List<io.qdrant.client.grpc.Points.PointId> allPointIds = new ArrayList<>();
                for (Picture p : pictureList) {
                    allPointIds.add(io.qdrant.client.grpc.Points.PointId.newBuilder().setNum(p.getId()).build());
                }

                // 批量查询存在的 ID (withPayload=false, withVectors=false 省宽带)
                // 若方法签名因版本不同报错，可使用全量默认方法 qdrantClient.retrieveAsync(collectionName, allPointIds)
                List<io.qdrant.client.grpc.Points.RetrievedPoint> existingPoints =
                        qdrantClient.retrieveAsync(collectionName, allPointIds, false, false, null).get();

                java.util.Set<Long> existingIdSet = new java.util.HashSet<>();
                if (existingPoints != null) {
                    for (io.qdrant.client.grpc.Points.RetrievedPoint rp : existingPoints) {
                        existingIdSet.add(rp.getId().getNum());
                    }
                }

                int beforeSize = pictureList.size();
                pictureList.removeIf(p -> existingIdSet.contains(p.getId()));
                int afterSize = pictureList.size();

                log.info("全量数据共 {}, 已存在 {}, 还需要同步 {}", beforeSize, existingIdSet.size(), afterSize);

                if (pictureList.isEmpty()) {
                    log.info("所有图片均已向量化完毕，无需同步");
                    return;
                }
            } catch (Exception e) {
                log.warn("获取已存在向量记录失败，将进行覆盖全量同步", e);
            }

            // 初始化进度总数，并推送首次通知
            VectorSyncController.totalCount.set(pictureList.size());
            VectorSyncController.distributeProgress();

            // 2. 限流器配置：每秒最多请求 10 次阿里云大模型
            // 你在百炼平台上额度充足，10 QPS 是一个安全且高效的阈值
            RateLimiter rateLimiter = RateLimiter.create(10.0);

            for (Picture picture : pictureList) {
                // 如果发现 Controller 层被要求中断任务（预留的关停机制）
                if (!VectorSyncController.isRunning.get()) {
                    log.warn("同步任务已人为终止");
                    break;
                }

                try {
                    // 阻塞直到获取到令牌
                    rateLimiter.acquire();

                    // 3. 调用阿里云获取图片向量，增加 3 次重试机制
                    List<Double> embedding = null;
                    int maxRetries = 3;
                    for (int i = 0; i < maxRetries; i++) {
                        try {
                            embedding = aliYunAiService.getImageEmbedding(picture.getUrl());
                            if (embedding != null && !embedding.isEmpty()) {
                                break; // 成功则跳出重试循环
                            }
                        } catch (Exception e) {
                            if (i == maxRetries - 1) {
                                log.error("大模型重试了 {} 次依然失败, url: {}", maxRetries, picture.getUrl(), e);
                                throw e; // 最后一次重试依然失败，抛出异常让外层 catch 处理
                            }
                            log.warn("调用大模型失败，准备第 {} 次重试, url: {}", i + 1, picture.getUrl());
                            Thread.sleep(500); // 稍微退避一下
                        }
                    }

                    if (embedding == null || embedding.isEmpty()) {
                        VectorSyncController.failedCount.incrementAndGet();
                        continue;
                    }

                    // 将 Double 转换为 Qdrant 需要的 Float 格式
                    List<Float> floatVector = new ArrayList<>();
                    for (Double d : embedding) {
                        floatVector.add(d.floatValue());
                    }

                    // 4. 构建 Qdrant 存储记录 (Point)
                    Points.PointStruct point = Points.PointStruct.newBuilder()
                            // ID 为图片主键
                            .setId(Points.PointId.newBuilder().setNum(picture.getId()).build())
                            // 填入 1024 维向量数据
                            .setVectors(Points.Vectors.newBuilder().setVector(
                                    Points.Vector.newBuilder().addAllData(floatVector).build()
                            ).build())
                            // 填入 Payload 附加字段（用于后续过滤检索）
                            .putPayload("picId", value(picture.getId()))
                            .putPayload("userId", value(picture.getUserId()))
                            .putPayload("spaceId", value(picture.getSpaceId() != null ? picture.getSpaceId() : 0L))
                            .build();

                    // 5. 写入到 Qdrant (UPSERT 操作具有幂等性，存在则更新，不存在则插入)
                    qdrantClient.upsertAsync(collectionName, List.of(point)).get();

                    VectorSyncController.processedCount.incrementAndGet();

                } catch (Exception e) {
                    log.error("处理图片向量化失败, picId: {}, url: {}", picture.getId(), picture.getUrl(), e);
                    VectorSyncController.failedCount.incrementAndGet();
                } finally {
                    // 每处理完一张，就通知所有连着网页的管理员进度条动一下
                    VectorSyncController.distributeProgress();
                }
            }
        } catch (Exception e) {
            log.error("向量同步跑批发生致命异常", e);
        } finally {
            log.info("全局向量化跑批任务结束，累计处理成功: {}, 失败: {}",
                    VectorSyncController.processedCount.get(),
                    VectorSyncController.failedCount.get());
            // 清理连接池与状态锁
            VectorSyncController.finishSync();
        }
    }
}
