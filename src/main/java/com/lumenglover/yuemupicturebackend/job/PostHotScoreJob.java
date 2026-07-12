package com.lumenglover.yuemupicturebackend.job;

import com.lumenglover.yuemupicturebackend.model.entity.Post;
import com.lumenglover.yuemupicturebackend.service.PostService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 帖子热榜分数计算任务
 * 该任务负责计算帖子的热榜分数
 */
@Slf4j
@Component
public class PostHotScoreJob implements CommandLineRunner {

    @Resource
    private PostService postService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private com.lumenglover.yuemupicturebackend.manager.RankingRedisManager rankingRedisManager;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    private static final int PAGE_SIZE = 1000; // 分页查询大小

    /**
     * 程序启动时异步执行一次热榜分数计算
     */
    @Override
    public void run(String... args) {
        log.info("程序启动，开始异步执行首次帖子热榜分数计算...");
        CompletableFuture.runAsync(this::calculateHotScores, taskExecutor);
    }

    /**
     * 每小时整点执行一次热榜分数计算
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行一次
    public void calculateHotScoresScheduled() {
        log.info("定时任务：开始计算帖子热榜分数...");
        calculateHotScores();
    }

    /**
     * 计算帖子的热榜分数
     */
    @Transactional(rollbackFor = Exception.class)
    public void calculateHotScores() {
        RLock lock = redissonClient.getLock("post:hot_score:calculate_lock");
        try {
            // 尝试获取分布式锁，等待10秒，持有300秒
            if (lock.tryLock(10, 300, TimeUnit.SECONDS)) {
                log.info("获取到分布式锁，开始计算帖子热榜分数...");
                calculateHotScoresFull();
            } else {
                log.warn("未能获取到分布式锁，跳过本次帖子热榜分数计算");
            }
        } catch (InterruptedException e) {
            log.error("获取分布式锁被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("计算帖子热榜分数时发生异常", e);
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
     * 全量计算热榜分数
     */
    private void calculateHotScoresFull() {
        log.info("开始全量计算帖子热榜分数...");

        long totalProcessed = 0;
        long offset = 0;
        Map<Long, Double> allPostScores = new HashMap<>();  // 收集所有帖子分数用于同步到Redis

        while (true) {
            // 分页查询审核通过的帖子
            List<Post> posts = postService.listPostsForHotScore(offset, PAGE_SIZE);

            if (posts.isEmpty()) {
                break;
            }

            // 计算每个帖子的热榜分数
            List<Post> postsToUpdate = new ArrayList<>();
            for (Post post : posts) {
                double hotScore = calculateHotScore(post);
                Post updatePost = new Post();
                updatePost.setId(post.getId());
                updatePost.setHotScore(hotScore);
                postsToUpdate.add(updatePost);

                // 收集分数用于同步到Redis
                allPostScores.put(post.getId(), hotScore);
            }

            // 批量更新热榜分数
            boolean updateResult = postService.updateBatchById(postsToUpdate);
            if (!updateResult) {
                log.error("批量更新帖子热榜分数失败，批次大小: {}", postsToUpdate.size());
            }

            totalProcessed += posts.size();
            log.info("已处理 {} 个帖子的热榜分数", totalProcessed);

            if (posts.size() < PAGE_SIZE) {
                break;
            }

            offset += PAGE_SIZE;
        }

        log.info("帖子热榜分数计算完成，共处理 {} 个帖子", totalProcessed);

        // 同步到 Redis
        syncPostRankingToRedis(allPostScores);
    }

    /**
     * 计算单个帖子的热榜分数
     * 帖子榜单权重：评论 > 点赞 > 分享 > 浏览
     *
     * 评分逻辑：
     *  - 使用对数压缩避免极端值
     *  - 按互动质量加权：评论权重最高（讨论性内容）
     *  - 考虑时间衰减，让新帖子有机会上榜
     */
    private double calculateHotScore(Post post) {
        // 各维度原始值
        double views    = post.getViewCount()    != null ? post.getViewCount()    : 0;
        double likes    = post.getLikeCount()    != null ? post.getLikeCount()    : 0;
        double comments = post.getCommentCount() != null ? post.getCommentCount() : 0;
        double shares   = post.getShareCount()   != null ? post.getShareCount()   : 0;

        // 使用对数压缩，并按互动质量加权
        // 帖子榜单权重：评论 > 点赞 > 分享 > 浏览
        double interactionScore = Math.log1p(views    * 0.1)   // 浏览量权重最低
                + Math.log1p(likes    * 3.0)                    // 点赞权重较高
                + Math.log1p(comments * 5.0)                    // 评论权重最高（讨论性）
                + Math.log1p(shares   * 4.0);                   // 分享权重高

        // 时间衰减因子（帖子需要考虑时效性）
        double timeDecay = 1.0;
        if (post.getCreateTime() != null) {
            long hoursSinceCreation = (System.currentTimeMillis() - post.getCreateTime().getTime()) / (1000 * 60 * 60);
            // 使用较缓和的时间衰减，让优质老帖子也能保持热度
            // 24小时内不衰减，之后每24小时衰减10%
            if (hoursSinceCreation > 24) {
                double daysSinceCreation = hoursSinceCreation / 24.0;
                timeDecay = Math.pow(0.9, daysSinceCreation - 1);  // 每天衰减10%
            }
        }

        // 最终分数 = 互动分数 * 时间衰减
        return interactionScore * timeDecay;
    }

    /**
     * 将帖子榜单同步到 Redis
     */
    private void syncPostRankingToRedis(Map<Long, Double> postScores) {
        try {
            if (postScores.isEmpty()) {
                log.warn("帖子分数为空，跳过同步到Redis");
                return;
            }

            // 删除旧的榜单缓存
            rankingRedisManager.deleteContentRanking("post", "hot");

            // 批量添加到 Redis ZSET
            rankingRedisManager.batchAddContentToRanking("post", "hot", postScores);

            log.info("帖子榜单同步到 Redis 成功，数量: {}", postScores.size());

        } catch (Exception e) {
            log.error("帖子榜单同步到 Redis 失败", e);
        }
    }
}
