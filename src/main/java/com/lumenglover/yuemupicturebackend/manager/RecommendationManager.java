package com.lumenglover.yuemupicturebackend.manager;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.model.entity.ViewRecord;
import com.lumenglover.yuemupicturebackend.service.ViewRecordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 推荐管理逻辑 (基于改进版 Item-CF)
 * 包含：SCAN安全清理、活跃用户惩罚、时间衰减推荐、高性能矩阵构建
 */
@Component
@Slf4j
public class RecommendationManager implements ApplicationRunner {

    @Resource
    private ViewRecordService viewRecordService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private Executor recommendationExecutor;

    // ==================== 常量配置 ====================
    private static final String SIMILARITY_MATRIX_PREFIX = "pic:sim:";
    private static final String POST_SIMILARITY_MATRIX_PREFIX = "post:sim:";
    private static final String RECOMMEND_POOL_PREFIX = "user:rec:pool:";
    private static final String POST_RECOMMEND_POOL_PREFIX = "user:post:rec:pool:";

    private static final long MATRIX_EXPIRE_TIME = 3600 * 24;
    private static final long POOL_EXPIRE_TIME = 600;

    private static final int MAX_RECORDS_SAMPLE = 10000;
    private static final int MAX_USER_HISTORY = 50;
    private static final int CF_POOL_SIZE = 200;
    private static final double MIN_SIMILARITY = 0.02;
    private static final int TOP_N_SIMILAR_ITEMS = 30;

    // 推荐兴趣的时间衰减系数 (控制近期偏好的权重)
    private static final double TIME_DECAY_RATE = 0.05;

    @Override
    public void run(ApplicationArguments args) {
        log.info("==================== 推荐系统初始化 ====================");

        // 启动前先安全清理所有旧数据
        clearAllOldSimilarityData();

        log.info("开始提交异步计算任务...");

        CompletableFuture.runAsync(() -> {
            try {
                updateSimilarityMatrixInternal(1, SIMILARITY_MATRIX_PREFIX, "图片");
            } catch (Exception e) {
                log.error("图片矩阵计算失败", e);
            }
        }, recommendationExecutor);

        CompletableFuture.runAsync(() -> {
            try {
                updateSimilarityMatrixInternal(2, POST_SIMILARITY_MATRIX_PREFIX, "帖子");
            } catch (Exception e) {
                log.error("帖子矩阵计算失败", e);
            }
        }, recommendationExecutor);

        log.info("推荐系统初始化完成 → 计算任务后台运行中");
    }

    /**
     * 🔥 安全清理：使用 SCAN 替代 keys *，防止单线程 Redis 阻塞
     */
    private void clearAllOldSimilarityData() {
        try {
            scanAndDelete(SIMILARITY_MATRIX_PREFIX + "*");
            scanAndDelete(POST_SIMILARITY_MATRIX_PREFIX + "*");
            scanAndDelete(RECOMMEND_POOL_PREFIX + "*");
            scanAndDelete(POST_RECOMMEND_POOL_PREFIX + "*");
            log.info("✅ 推荐系统旧缓存全部安全清理完成 (基于 SCAN)");
        } catch (Exception e) {
            log.error("清理旧缓存失败", e);
        }
    }

    /**
     * 底层 SCAN 删除实现，按批次删除，对内存和 Redis 性能极其友好
     */
    private void scanAndDelete(String pattern) {
        stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(1000).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                List<byte[]> keysToDelete = new ArrayList<>();
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                    if (keysToDelete.size() >= 500) {
                        connection.del(keysToDelete.toArray(new byte[0][0]));
                        keysToDelete.clear();
                    }
                }
                if (!keysToDelete.isEmpty()) {
                    connection.del(keysToDelete.toArray(new byte[0][0]));
                }
            } catch (Exception e) {
                log.error("SCAN 删除 Redis Key 失败，pattern: {}", pattern, e);
            }
            return null;
        });
    }

    @Scheduled(cron = "0 10 * * * ?")
    public void updateSimilarityMatrix() {
        CompletableFuture.runAsync(() -> updateSimilarityMatrixInternal(1, SIMILARITY_MATRIX_PREFIX, "图片"), recommendationExecutor);
    }

    @Scheduled(cron = "0 40 * * * ?")
    public void updatePostSimilarityMatrix() {
        CompletableFuture.runAsync(() -> updateSimilarityMatrixInternal(2, POST_SIMILARITY_MATRIX_PREFIX, "帖子"), recommendationExecutor);
    }

    private void updateSimilarityMatrixInternal(int targetType, String redisPrefix, String logName) {
        log.info("开始更新{}相似度矩阵...", logName);
        long startTime = System.currentTimeMillis();

        QueryWrapper<ViewRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("targetType", targetType)
                .select("userId", "targetId")
                .orderByDesc("updateTime")
                .last("LIMIT " + MAX_RECORDS_SAMPLE);

        List<ViewRecord> records = viewRecordService.list(queryWrapper);
        if (CollUtil.isEmpty(records)) {
            log.info("暂无{}浏览记录，跳过矩阵更新", logName);
            return;
        }

        // 1. 构建用户 -> 物品列表映射
        Map<Long, Set<Long>> userViewMap = new HashMap<>();
        // 2. 统计每个物品被多少用户看过 (用于分母计算)
        Map<Long, Integer> itemCount = new HashMap<>();

        for (ViewRecord record : records) {
            Long userId = record.getUserId();
            Long targetId = record.getTargetId();

            Set<Long> userItems = userViewMap.computeIfAbsent(userId, k -> new LinkedHashSet<>());
            if (userItems.size() < MAX_USER_HISTORY) {
                if (userItems.add(targetId)) {
                    itemCount.merge(targetId, 1, Integer::sum);
                }
            }
        }
        records.clear(); // 释放原记录集合

        // 🔥 高性能构建共现矩阵 (时间复杂度极低，直接抛弃旧版 O(I^2*U) 逻辑)
        Map<Long, Map<Long, Double>> coOccurrenceMatrix = new HashMap<>();

        for (Map.Entry<Long, Set<Long>> entry : userViewMap.entrySet()) {
            List<Long> items = new ArrayList<>(entry.getValue());
            int userItemCount = items.size();
            if (userItemCount <= 1) continue;

            // 🔥 活跃用户惩罚：看图越多的用户，提供的相似度权重越低
            double userWeight = 1.0 / Math.log1p(userItemCount);

            for (int i = 0; i < items.size(); i++) {
                Long itemA = items.get(i);
                for (int j = i + 1; j < items.size(); j++) {
                    Long itemB = items.get(j);

                    coOccurrenceMatrix.computeIfAbsent(itemA, k -> new HashMap<>())
                            .merge(itemB, userWeight, Double::sum);
                    coOccurrenceMatrix.computeIfAbsent(itemB, k -> new HashMap<>())
                            .merge(itemA, userWeight, Double::sum);
                }
            }
        }
        userViewMap.clear();

        // 4. 计算余弦相似度并写入 Redis
        int savedCount = 0;
        for (Map.Entry<Long, Map<Long, Double>> entry : coOccurrenceMatrix.entrySet()) {
            Long itemA = entry.getKey();
            Map<Long, Double> relatedItems = entry.getValue();
            int na = itemCount.getOrDefault(itemA, 0);

            PriorityQueue<SimilarityPair> topN = new PriorityQueue<>(
                    Comparator.comparingDouble(SimilarityPair::getSimilarity));

            for (Map.Entry<Long, Double> relatedEntry : relatedItems.entrySet()) {
                Long itemB = relatedEntry.getKey();
                int nb = itemCount.getOrDefault(itemB, 0);
                double coWeight = relatedEntry.getValue();

                // 余弦相似度公式
                double similarity = coWeight / Math.sqrt((double) na * nb);

                if (similarity > MIN_SIMILARITY) {
                    topN.offer(new SimilarityPair(itemB, similarity));
                    if (topN.size() > TOP_N_SIMILAR_ITEMS) {
                        topN.poll();
                    }
                }
            }

            if (!topN.isEmpty()) {
                String redisKey = redisPrefix + itemA;
                Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
                for (SimilarityPair pair : topN) {
                    tuples.add(ZSetOperations.TypedTuple.of(pair.getItemId().toString(), pair.getSimilarity()));
                }
                stringRedisTemplate.opsForZSet().add(redisKey, tuples);
                stringRedisTemplate.expire(redisKey, MATRIX_EXPIRE_TIME, TimeUnit.SECONDS);
                savedCount++;
            }
        }

        long endTime = System.currentTimeMillis();
        log.info("{}相似度矩阵更新完成，耗时: {}ms，保存了 {} 个实体", logName, (endTime - startTime), savedCount);
    }

    private List<Long> calculateFullRecommendationPool(Long userId, int targetType, String matrixPrefix) {
        QueryWrapper<ViewRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId)
                .eq("targetType", targetType)
                .orderByDesc("updateTime")
                .last("limit " + MAX_USER_HISTORY)
                .select("targetId", "updateTime"); // 🔥 需要拿时间做衰减

        List<ViewRecord> historyRecords = viewRecordService.list(queryWrapper);
        if (CollUtil.isEmpty(historyRecords)) {
            return Collections.emptyList();
        }

        Map<Long, Double> rankMap = new HashMap<>();
        Set<Long> historySet = historyRecords.stream().map(ViewRecord::getTargetId).collect(Collectors.toSet());
        Instant now = Instant.now();

        for (ViewRecord record : historyRecords) {
            Long historyId = record.getTargetId();
            String redisKey = matrixPrefix + historyId;

            // 🔥 时间衰减：用户越久远的浏览记录，对当前推荐的权重越小
            long daysDiff = ChronoUnit.DAYS.between(record.getUpdateTime().toInstant(), now);
            double timeWeight = Math.exp(-TIME_DECAY_RATE * Math.max(0, daysDiff));

            try {
                Set<ZSetOperations.TypedTuple<String>> similarities = stringRedisTemplate.opsForZSet()
                        .reverseRangeWithScores(redisKey, 0, -1);

                if (similarities != null) {
                    for (ZSetOperations.TypedTuple<String> tuple : similarities) {
                        Long relatedId = Long.valueOf(tuple.getValue());
                        Double similarity = tuple.getScore();

                        if (historySet.contains(relatedId) || similarity == null) {
                            continue;
                        }

                        // 综合得分 = 原相似度 * 历史记录的时间衰减权重
                        double finalScore = similarity * timeWeight;
                        rankMap.put(relatedId, rankMap.getOrDefault(relatedId, 0.0) + finalScore);
                    }
                }
            } catch (Exception e) {
                log.warn("获取相似实体失败，key:{}", redisKey, e);
            }
        }

        return rankMap.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(CF_POOL_SIZE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<Long> getCFRecommendationList(Long userId) {
        return getCFRecommendationListInternal(userId, 1, SIMILARITY_MATRIX_PREFIX, RECOMMEND_POOL_PREFIX, "图片");
    }

    public List<Long> getPostCFRecommendationList(Long userId) {
        return getCFRecommendationListInternal(userId, 2, POST_SIMILARITY_MATRIX_PREFIX, POST_RECOMMEND_POOL_PREFIX, "帖子");
    }

    private List<Long> getCFRecommendationListInternal(Long userId, int targetType, String matrixPrefix,
                                                       String poolPrefix, String logName) {
        String poolKey = poolPrefix + userId;
        List<String> idStrings = stringRedisTemplate.opsForList().range(poolKey, 0, -1);

        if (CollUtil.isNotEmpty(idStrings)) {
            return idStrings.stream().map(Long::valueOf).collect(Collectors.toList());
        }

        List<Long> cfPool = calculateFullRecommendationPool(userId, targetType, matrixPrefix);

        if (CollUtil.isNotEmpty(cfPool)) {
            List<String> idStrs = cfPool.stream().map(String::valueOf).collect(Collectors.toList());
            stringRedisTemplate.delete(poolKey);
            stringRedisTemplate.opsForList().rightPushAll(poolKey, idStrs);
            stringRedisTemplate.expire(poolKey, POOL_EXPIRE_TIME, TimeUnit.SECONDS);
        }
        return cfPool;
    }

    public void manualUpdateSimilarityMatrix() {
        updateSimilarityMatrix();
        updatePostSimilarityMatrix();
    }

    public void clearUserRecommendationCache(Long userId) {
        stringRedisTemplate.delete(RECOMMEND_POOL_PREFIX + userId);
        stringRedisTemplate.delete(POST_RECOMMEND_POOL_PREFIX + userId);
    }

    public void clearHotPoolCache() {
        stringRedisTemplate.delete("picture:recommend:pool");
        stringRedisTemplate.delete("post:recommend:pool");
        log.info("✅ 已清空全局推荐池缓存");
    }

    public void clearAllRecommendationCache() {
        scanAndDelete(RECOMMEND_POOL_PREFIX + "*");
        scanAndDelete(POST_RECOMMEND_POOL_PREFIX + "*");
        scanAndDelete("user:picture:history:*");
        scanAndDelete("user:post:history:*");
        scanAndDelete("user:picture:cf:*");
        scanAndDelete("user:post:cf:*");

        stringRedisTemplate.delete("picture:recommend:pool");
        stringRedisTemplate.delete("post:recommend:pool");

        log.info("✅ 已清空所有推荐相关缓存");
    }

    private static class SimilarityPair {
        private final Long itemId;
        private final double similarity;

        public SimilarityPair(Long itemId, double similarity) {
            this.itemId = itemId;
            this.similarity = similarity;
        }

        public Long getItemId() {
            return itemId;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}
