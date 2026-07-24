package com.lumenglover.yuemupicturebackend.job;

import com.lumenglover.yuemupicturebackend.constant.RedisConstant;
import com.lumenglover.yuemupicturebackend.model.dto.picture.PictureHotScoreDto;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import com.lumenglover.yuemupicturebackend.utils.PictureScoreUpdateTracker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 图片热榜分数计算任务
 * 该任务负责计算图片的热榜分数，不偏向新内容
 */
@Slf4j
@Component
public class PictureHotScoreJob implements CommandLineRunner {

    @Resource
    private PictureService pictureService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PictureScoreUpdateTracker pictureScoreUpdateTracker;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    @Resource
    private com.lumenglover.yuemupicturebackend.manager.RankingRedisManager rankingRedisManager;

    private static final int BATCH_SIZE = 50; // 每个小批次的大小
    private static final int PAGE_SIZE = 1000; // 分页查询大小

    /**
     * 程序启动时异步执行一次热榜分数计算
     */
    @Override
    public void run(String... args) {
        log.info("程序启动，开始异步执行首次热榜分数计算...");
        CompletableFuture.runAsync(this::calculateHotScores, taskExecutor);
    }

    /**
     * 每小时整点执行一次热榜分数计算
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行一次
    public void calculateHotScoresScheduled() {
        log.info("定时任务：开始计算图片热榜分数...");
        calculateHotScores();
    }

    /**
     * 计算图片的热榜分数
     * 优先处理增量更新队列，若队列为空则处理全量数据
     */
    @Transactional(rollbackFor = Exception.class)
    public void calculateHotScores() {
        RLock lock = redissonClient.getLock("picture:hot_score:calculate_lock");
        try {
            // 尝试获取分布式锁，等待10秒，持有300秒
            if (lock.tryLock(10, 300, TimeUnit.SECONDS)) {
                log.info("获取到分布式锁，开始计算图片热榜分数...");

                // 先尝试处理增量更新队列
                long queueSize = pictureScoreUpdateTracker.getHotScoreQueueSize();
                if (queueSize > 0) {
                    log.info("发现 {} 个待更新的图片，开始增量计算...", queueSize);
                    calculateHotScoresIncrementally();
                } else {
                    log.info("增量更新队列为空，开始全量计算...");
                    calculateHotScoresFull();
                }
            } else {
                log.warn("未能获取到分布式锁，跳过本次热榜分数计算");
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("计算热榜分数时发生异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();
                    log.info("已释放分布式锁");
                } catch (Exception e) {
                    log.error("释放分布式锁失败", e);
                }
            }
        }
    }

    /**
     * 增量计算热榜分数
     */
    private void calculateHotScoresIncrementally() {
        // 获取待更新的图片ID
        Set<String> pictureIds = pictureScoreUpdateTracker.getPictureIdsForHotScoreUpdate();
        if (pictureIds == null || pictureIds.isEmpty()) {
            log.info("增量更新队列为空，无需计算");
            return;
        }

        log.info("开始处理 {} 个待更新的图片ID", pictureIds.size());

        // 将图片ID转换为Long类型列表
        List<Long> ids = pictureIds.stream()
                .map(Long::valueOf)
                .collect(Collectors.toList());

        // 分批处理
        for (int i = 0; i < ids.size(); i += PAGE_SIZE) {
            int endIndex = Math.min(i + PAGE_SIZE, ids.size());
            List<Long> batchIds = ids.subList(i, endIndex);

            // 查询需要更新的图片数据
            List<PictureHotScoreDto> pictureDtos = pictureService.listByIds(batchIds).stream()
                    .filter(p -> p.getSpaceId() == null && p.getReviewStatus() == 1 && p.getIsDraft() == 0) // 只处理公共空间、审核通过且非草稿状态的图片
                    .map(p -> {
                        PictureHotScoreDto dto = new PictureHotScoreDto();
                        dto.setId(p.getId());
                        dto.setViewCount(p.getViewCount());
                        dto.setLikeCount(p.getLikeCount());
                        dto.setCommentCount(p.getCommentCount());
                        dto.setShareCount(p.getShareCount());
                        return dto;
                    })
                    .collect(Collectors.toList());

            if (!pictureDtos.isEmpty()) {
                // 计算热榜分数
                List<Picture> picturesToUpdate = new ArrayList<>();
                for (PictureHotScoreDto dto : pictureDtos) {
                    double hotScore = calculateHotScore(dto);
                    Picture picture = new Picture();
                    picture.setId(dto.getId());
                    picture.setHotScore(hotScore);
                    picturesToUpdate.add(picture);
                }

                // 批量更新热榜分数
                boolean updateResult = pictureService.updateBatchHotScore(picturesToUpdate);
                if (!updateResult) {
                    log.error("批量更新热榜分数失败，批次大小: {}", picturesToUpdate.size());
                } else {
                    log.info("成功更新 {} 个图片的热榜分数", picturesToUpdate.size());
                }
            }
        }

        // 从更新队列中移除已处理的图片ID
        pictureScoreUpdateTracker.removePicturesFromHotScoreUpdateQueue(pictureIds);
        log.info("增量计算完成，已从更新队列中移除 {} 个图片ID", pictureIds.size());
    }

    /**
     * 全量计算热榜分数
     */
    private void calculateHotScoresFull() {
        log.info("开始全量计算图片热榜分数...");

        // 获取总图片数量
        long totalPictures = pictureService.countPictureScoreData();
        log.info("总共需要处理 {} 张图片", totalPictures);
        calculateHotScoresWithoutSharding();
    }

    private void calculateHotScoresWithoutSharding() {
        log.info("图片数量较少，使用顺序处理模式");
        long totalProcessed = 0;
        long offset = 0;
        Map<Long, Double> allPictureScores = new HashMap<>();  // 收集所有图片分数用于同步到Redis

        while (true) {
            // 分页查询用于计算热榜分数的图片数据
            List<PictureHotScoreDto> pictureDtos = pictureService.selectPictureScoreData(offset, PAGE_SIZE).stream()
                    .filter(dto -> {
                        // 获取完整的图片实体以检查空间、审核状态和草稿状态
                        Picture picture = pictureService.getById(dto.getId());
                        return picture != null && picture.getSpaceId() == null &&
                                picture.getReviewStatus() == 1 && picture.getIsDraft() == 0;
                    })
                    .collect(Collectors.toList());

            if (pictureDtos.isEmpty()) {
                break;
            }

            // 计算每张图片的热榜分数
            List<Picture> picturesToUpdate = new ArrayList<>();
            for (PictureHotScoreDto dto : pictureDtos) {
                double hotScore = calculateHotScore(dto);
                Picture picture = new Picture();
                picture.setId(dto.getId());
                picture.setHotScore(hotScore);
                picturesToUpdate.add(picture);

                // 收集分数用于同步到Redis
                allPictureScores.put(dto.getId(), hotScore);
            }

            // 分批更新热榜分数
            boolean updateResult = pictureService.updateBatchHotScore(picturesToUpdate);
            if (!updateResult) {
                log.error("批量更新热榜分数失败，批次大小: {}", picturesToUpdate.size());
            }

            totalProcessed += pictureDtos.size();
            log.info("已处理 {} 张图片的热榜分数", totalProcessed);

            if (pictureDtos.size() < PAGE_SIZE) {
                break;
            }

            offset += PAGE_SIZE;
        }

        log.info("顺序计算完成，共处理 {} 张图片", totalProcessed);

        // 同步到 Redis
        syncPictureRankingToRedis(allPictureScores);
    }

    /**
     * 计算单张图片的热榜分数（不带时间衰减）
     * 图片是长期有效的内容，不应该因为发布时间而降低排名
     * 完全基于互动数据计算分数，让优质内容长期保持热度
     *
     * 评分逻辑：
     *  - 使用对数压缩避免极端值
     *  - 按互动质量加权：评论 > 分享 > 点赞 > 浏览
     *  - 不考虑发布时间，老图片和新图片公平竞争
     */
    private double calculateHotScore(PictureHotScoreDto dto) {
        // 各维度原始值
        double views    = dto.getViewCount()    != null ? dto.getViewCount()    : 0;
        double likes    = dto.getLikeCount()    != null ? dto.getLikeCount()    : 0;
        double comments = dto.getCommentCount() != null ? dto.getCommentCount() : 0;
        double shares   = dto.getShareCount()   != null ? dto.getShareCount()   : 0;

        // 使用对数压缩，并按互动质量加权
        // 图片榜单权重：评论 > 分享 > 点赞 > 浏览
        double interactionScore = Math.log1p(views   * 0.1)   // 浏览量权重最低
                + Math.log1p(likes    * 2.0)                   // 点赞权重适中
                + Math.log1p(comments * 3.0)                   // 评论权重高
                + Math.log1p(shares   * 2.5);                  // 分享权重较高

        // 直接返回互动分数，不进行时间衰减
        // 这样可以让优质的老图片持续保持热度
        return interactionScore;
    }

    /**
     * 将图片榜单同步到 Redis
     */
    private void syncPictureRankingToRedis(Map<Long, Double> pictureScores) {
        try {
            if (pictureScores.isEmpty()) {
                log.warn("图片分数为空，跳过同步到Redis");
                return;
            }

            // 删除旧的榜单缓存
            rankingRedisManager.deleteContentRanking("picture", "hot");

            // 批量添加到 Redis ZSET
            rankingRedisManager.batchAddContentToRanking("picture", "hot", pictureScores);

            log.info("图片榜单同步到 Redis 成功，数量: {}", pictureScores.size());

        } catch (Exception e) {
            log.error("图片榜单同步到 Redis 失败", e);
        }
    }
}
