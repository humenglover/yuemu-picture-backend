package com.lumenglover.yuemupicturebackend.job;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.common.util.concurrent.RateLimiter;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.service.AliYunAiService;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Qualifier;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

import static io.qdrant.client.ValueFactory.value;

/**
 * 增量同步图片数据到 Qdrant 向量数据库的定时任务
 */
@Component
@Slf4j
public class QdrantSyncJob {

    @Resource
    private PictureService pictureService;

    @Resource
    private AliYunAiService aliYunAiService;

    @Resource
    private QdrantClient qdrantClient;

    @Resource
    @Qualifier("asyncExecutor")
    private Executor asyncExecutor;

    @Value("${qdrant.collection-name:yuemu_picture}")
    private String collectionName;

    // 限流器：每秒 10 次请求，防止打满阿里云大模型接口
    private final RateLimiter rateLimiter = RateLimiter.create(10.0);

    /**
     * 每 5 分钟执行一次，增量同步过去 10 分钟内新增或修改的图片
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void syncRecentPicturesToQdrant() {
        log.info("开始执行增量同步 Qdrant 定时任务...");

        // 获取过去 10 分钟的时间
        long tenMinutesAgo = System.currentTimeMillis() - 10 * 60 * 1000L;
        Date targetTime = new Date(tenMinutesAgo);

        // 查询过去 10 分钟内修改或创建的图片（不再限制审核状态，只要没被删除就同步）
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.ge("editTime", targetTime);
        List<Picture> pictureList = pictureService.list(queryWrapper);

        if (CollUtil.isEmpty(pictureList)) {
            log.info("过去 10 分钟没有需要同步的图片，任务结束");
            return;
        }

        log.info("共查询到 {} 张最近修改/新增的图片，开始同步到 Qdrant...", pictureList.size());
        int successCount = 0;
        int failCount = 0;

        for (Picture picture : pictureList) {
            try {
                // 限流，阻塞等待令牌
                rateLimiter.acquire();

                // 获取图片特征向量（附带 3 次重试）
                List<Double> embedding = null;
                int maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        embedding = aliYunAiService.getImageEmbedding(picture.getUrl());
                        if (embedding != null && !embedding.isEmpty()) {
                            break;
                        }
                    } catch (Exception e) {
                        if (i == maxRetries - 1) {
                            log.error("大模型重试提取特征 {} 次依然失败, picId: {}", maxRetries, picture.getId(), e);
                        } else {
                            log.warn("大模型提取特征失败，准备第 {} 次重试, picId: {}", i + 1, picture.getId());
                            Thread.sleep(500);
                        }
                    }
                }

                if (embedding == null || embedding.isEmpty()) {
                    failCount++;
                    continue;
                }

                // 转换为 Qdrant 需要的 Float 集合
                List<Float> floatVector = new ArrayList<>();
                for (Double d : embedding) {
                    floatVector.add(d.floatValue());
                }

                // 构建 Point
                Points.PointStruct point = Points.PointStruct.newBuilder()
                        .setId(Points.PointId.newBuilder().setNum(picture.getId()).build())
                        .setVectors(Points.Vectors.newBuilder().setVector(
                                Points.Vector.newBuilder().addAllData(floatVector).build()
                        ).build())
                        .putPayload("picId", value(picture.getId()))
                        .putPayload("userId", value(picture.getUserId()))
                        .putPayload("spaceId", value(picture.getSpaceId() != null ? picture.getSpaceId() : 0L))
                        .build();

                // UPSERT 到 Qdrant
                qdrantClient.upsertAsync(collectionName, List.of(point)).get();
                successCount++;

            } catch (Exception e) {
                log.error("图片增量同步到 Qdrant 失败, picId: {}", picture.getId(), e);
                failCount++;
            }
        }

        log.info("增量同步 Qdrant 定时任务结束。成功: {}, 失败: {}", successCount, failCount);
    }

    @Resource
    private com.lumenglover.yuemupicturebackend.service.VectorSyncService vectorSyncService;

    /**
     * 每天凌晨 2 点异步执行一次全量数据一致性校验兜底
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void consistencyCheckAsync() {
        // 使用线程池执行校验，避免裸 new Thread() 导致线程泄漏
        asyncExecutor.execute(() -> {
            log.info("开始执行 Qdrant 数据一致性校验(异步兜底)...");
            try {
                // 1. 获取 MySQL 中有效图片总数（不区分审核状态，但由于 MyBatis-Plus 全局逻辑删除所以不包括已删除数据）
                QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
                long mysqlCount = pictureService.count(queryWrapper);

                // 2. 获取 Qdrant 中的向量总数
                io.qdrant.client.grpc.Collections.CollectionInfo collectionInfo =
                        qdrantClient.getCollectionInfoAsync(collectionName).get();
                long qdrantCount = collectionInfo.getPointsCount();

                log.info("Qdrant 数据一致性校验 -> MySQL: {}, Qdrant: {}", mysqlCount, qdrantCount);

                // 3. 校验阈值：如果差异超过 50（由于限流漏掉的大量数据积压），则自动触发一次全量同步
                if (Math.abs(mysqlCount - qdrantCount) > 50) {
                    log.warn("检测到 MySQL 与 Qdrant 向量库数据差异过大 (差异: {})，正在触发异步全量同步兜底...",
                            Math.abs(mysqlCount - qdrantCount));
                    vectorSyncService.runFullSyncAsync();
                } else {
                    log.info("MySQL 与 Qdrant 数据差异在安全范围内，校验通过。");
                }
            } catch (Exception e) {
                log.error("Qdrant 数据一致性校验发生异常", e);
            }
        });
    }
}
