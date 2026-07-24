package com.lumenglover.yuemupicturebackend.job;

import com.lumenglover.yuemupicturebackend.model.dto.picture.PictureHotScoreDto;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import com.lumenglover.yuemupicturebackend.utils.PictureScoreUpdateTracker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
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
 * 图片推荐分数计算任务（采用 Hacker News 时间重力衰减算法）
 * 增量更新（每30分钟） + 定时全量校准，平衡时效性与稳定性
 */
@Slf4j
@Component
public class PictureRecommendScoreJob implements CommandLineRunner {

    @Resource
    private PictureService pictureService;

    @Resource
    private com.lumenglover.yuemupicturebackend.service.UserService userService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PictureScoreUpdateTracker pictureScoreUpdateTracker;

    @Resource(name = "taskExecutor")
    private Executor taskExecutor;

    // 核心权重配置（从配置文件读取）
    @Value("${recommend.score.view:0.12}")
    private double viewWeight;

    @Value("${recommend.score.like:0.15}")
    private double likeWeight;

    @Value("${recommend.score.comment:0.13}")
    private double commentWeight;

    @Value("${recommend.score.share:0.10}")
    private double shareWeight;

    // Hacker News 算法重力常数 G（推荐值 1.5 - 1.8）
    @Value("${recommend.score.gravity:1.8}")
    private double gravity;

    // 赋予内容的虚拟基础分，解决 0 互动新图得分为 0 被永远沉底的问题
    @Value("${recommend.score.base:1.5}")
    private double baseScore;

    // 分数平滑系数
    @Value("${recommend.score.smooth.alpha:0.7}")
    private double smoothAlpha;

    // 新图免平滑保护时间（小时）
    @Value("${recommend.new.picture.hours:24}")
    private long newPictureHours;

    // 增量更新配置
    @Value("${recommend.increment.max-size:500}")
    private int recommendIncrementMaxSize;

    // 批量配置
    private static final int BATCH_UPDATE_SIZE = 100;
    private static final int PAGE_SIZE = 500;
    private static final int SHARD_SIZE = 5000;
    private static final long CACHE_EXPIRE_HOURS = 12;
    private static final long CACHE_THRESHOLD_HOURS = 720;

    // 分数计算常量
    private static final double LOG_MULTIPLIER = 0.7;
    private static final double MIN_SCORE = 0.1;
    // 分数放大系数（使存入数据库的衰减分数更好看，不影响相对排序）
    private static final double SCORE_SCALE_FACTOR = 100.0;

    // 锁常量
    private static final String INCREMENT_LOCK_KEY = "picture:recommend_score:increment_lock";
    private static final String FULL_LOCK_KEY = "picture:recommend_score:full_lock";

    @Override
    public void run(String... args) {
        log.info("程序启动，开始初始化推荐分数（Hacker News 算法）...");
        CompletableFuture.runAsync(this::calculateRecommendScoresFull, taskExecutor);
    }

    @Scheduled(cron = "0 */30 * * * ?")
    public void calculateRecommendScoresIncrementallySchedule() {
        RLock lock = redissonClient.getLock(INCREMENT_LOCK_KEY);
        try {
            if (lock.tryLock(5, 20, TimeUnit.SECONDS)) {
                long queueSize = pictureScoreUpdateTracker.getRecommendScoreQueueSize();
                if (queueSize > 0) {
                    log.info("增量定时任务：发现{}个待更新图片，开始处理（单次最大{}条）",
                            queueSize, recommendIncrementMaxSize);
                    calculateRecommendScoresIncrementally(Instant.now(), true);
                }
            }
        } catch (InterruptedException e) {
            log.error("增量任务获取锁被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("增量任务执行异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void calculateRecommendScoresFullSchedule() {
        log.info("全量校准任务：开始执行（Hacker News 算法）");
        calculateRecommendScoresFull();
    }

    private void calculateRecommendScoresIncrementally(Instant now, boolean isLimit) {
        Set<String> pictureIds = isLimit
                ? pictureScoreUpdateTracker.getPictureIdsForRecommendScoreUpdateLimit(recommendIncrementMaxSize)
                : pictureScoreUpdateTracker.getPictureIdsForRecommendScoreUpdate();

        if (pictureIds == null || pictureIds.isEmpty()) {
            return;
        }

        List<Long> ids = pictureIds.stream().map(Long::valueOf).collect(Collectors.toList());
        Map<Boolean, List<Long>> idGroup = pictureService.groupPictureByNew(ids, newPictureHours);

        if (!idGroup.getOrDefault(true, Collections.emptyList()).isEmpty()) {
            processPictureBatch(pictureService.listByIds(idGroup.get(true)), now, true, true);
        }

        if (!idGroup.getOrDefault(false, Collections.emptyList()).isEmpty()) {
            processPictureBatch(pictureService.listByIds(idGroup.get(false)), now, true, false);
        }

        pictureScoreUpdateTracker.removePicturesFromRecommendScoreUpdateQueue(pictureIds);
        log.info("增量任务完成：处理{}个图片", pictureIds.size());
    }

    private void calculateRecommendScoresFull() {
        RLock lock = redissonClient.getLock(FULL_LOCK_KEY);
        try {
            if (lock.tryLock(10, 300, TimeUnit.SECONDS)) {
                long totalPictures = pictureService.countPictureScoreData();
                log.info("全量校准任务：共需处理{}张图片", totalPictures);

                Instant now = Instant.now();
                calculateRecommendScoresWithoutSharding(now);
            } else {
                log.warn("全量任务未获取到锁，跳过本次校准");
            }
        } catch (InterruptedException e) {
            log.error("全量任务获取锁被中断", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("全量任务执行异常", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private void calculateRecommendScoresWithoutSharding(Instant now) {
        long totalProcessed = 0;
        long offset = 0;
        while (true) {
            List<PictureHotScoreDto> pictureDtos = pictureService.selectPictureScoreData(offset, PAGE_SIZE);
            if (pictureDtos.isEmpty()) break;

            List<Long> ids = pictureDtos.stream().map(PictureHotScoreDto::getId).collect(Collectors.toList());
            totalProcessed += processPictureDtoBatch(pictureDtos, pictureService.listByIds(ids), now);

            if (pictureDtos.size() < PAGE_SIZE) break;
            offset += PAGE_SIZE;
        }
        log.info("全量顺序处理完成：共处理{}张图片", totalProcessed);
    }

    private long processPictureDtoBatch(List<PictureHotScoreDto> pictureDtos, List<Picture> pictures, Instant now) {
        List<Picture> picturesToUpdate = new ArrayList<>(BATCH_UPDATE_SIZE);
        for (int i = 0; i < pictureDtos.size(); i++) {
            Picture picture = pictures.get(i);
            long hours = ChronoUnit.HOURS.between(picture.getCreateTime().toInstant(), now);

            Double cachedScore = getCachedScore(picture.getId());
            double newScore = cachedScore != null ? cachedScore : calculateScoreFromDto(picture, pictureDtos.get(i), hours);

            // 传入存活时间来进行平滑判断
            double smoothScore = smoothScore(picture.getRecommendScore(), newScore, hours);

            picturesToUpdate.add(buildUpdatePicture(picture.getId(), smoothScore));
            if (picturesToUpdate.size() >= BATCH_UPDATE_SIZE) {
                batchUpdateScores(picturesToUpdate);
                picturesToUpdate.clear();
            }
        }
        if (!picturesToUpdate.isEmpty()) batchUpdateScores(picturesToUpdate);
        return pictureDtos.size();
    }

    private void processPictureBatch(List<Picture> pictures, Instant now, boolean isIncremental, boolean isNewPicture) {
        List<Picture> picturesToUpdate = new ArrayList<>(BATCH_UPDATE_SIZE);
        for (Picture picture : pictures) {
            long hours = ChronoUnit.HOURS.between(picture.getCreateTime().toInstant(), now);

            Double cachedScore = isIncremental ? null : getCachedScore(picture.getId());
            double newScore = cachedScore != null ? cachedScore : calculateScore(picture, hours);

            double smoothScore = smoothScore(picture.getRecommendScore(), newScore, hours);

            picturesToUpdate.add(buildUpdatePicture(picture.getId(), smoothScore));
            if (picturesToUpdate.size() >= BATCH_UPDATE_SIZE) {
                batchUpdateScores(picturesToUpdate);
                picturesToUpdate.clear();
            }
        }
        if (!picturesToUpdate.isEmpty()) batchUpdateScores(picturesToUpdate);
    }

    /**
     * 分数平滑核心方法（修复：使用时间而不是硬编码的分数阈值）
     */
    private double smoothScore(Double oldScore, double newScore, long hours) {
        // 如果是新图（newPictureHours 以内），或者没有旧分数，直接使用新分数，不做阻尼平滑
        if (oldScore == null || oldScore <= 0 || hours <= newPictureHours) {
            return Math.max(newScore, MIN_SCORE);
        }

        // 对于老内容，才进行平滑处理
        double smoothScore = oldScore * smoothAlpha + newScore * (1 - smoothAlpha);

        // 限制单次分数变化幅度±10%
        double maxChange = oldScore * 0.1;
        if (smoothScore > oldScore + maxChange) {
            smoothScore = oldScore + maxChange;
        } else if (smoothScore < oldScore - maxChange) {
            smoothScore = oldScore - maxChange;
        }
        return Math.max(smoothScore, MIN_SCORE);
    }

    private Picture buildUpdatePicture(Long id, double score) {
        Picture picture = new Picture();
        picture.setId(id);
        picture.setRecommendScore(score);
        return picture;
    }

    /**
     * 从 DTO 计算分数 (应用 Hacker News 算法)
     */
    private double calculateScoreFromDto(Picture picture, PictureHotScoreDto dto, long hours) {
        // 分子 P: 计算对数加权后的综合互动分数
        double p = Math.log1p(Optional.ofNullable(dto.getViewCount()).orElse(0L) * LOG_MULTIPLIER) * viewWeight +
                Math.log1p(Optional.ofNullable(dto.getLikeCount()).orElse(0L) * LOG_MULTIPLIER) * likeWeight +
                Math.log1p(Optional.ofNullable(dto.getCommentCount()).orElse(0L) * LOG_MULTIPLIER) * commentWeight +
                Math.log1p(Optional.ofNullable(dto.getShareCount()).orElse(0L) * LOG_MULTIPLIER) * shareWeight;

        // 核心修复：加上基础分，让新图片拥有初始降落伞
        p += baseScore;

        // 分母 (T + 2)^G: 时间衰减系数
        double timeDecay = Math.pow(hours + 2.0, gravity);

        // Hacker News 公式: P / (T + 2)^G  (乘上 SCORE_SCALE_FACTOR 提高可读性)
        double finalScore = (p * SCORE_SCALE_FACTOR) / timeDecay;

        // 机器人降权逻辑保留
        if (isBotUser(picture.getUserId())) {
            finalScore = finalScore * 0.3;
        }

        return Math.max(finalScore, MIN_SCORE);
    }

    /**
     * 计算单张图片分数 (应用 Hacker News 算法)
     */
    private double calculateScore(Picture picture, long hours) {
        long viewCount = Optional.ofNullable(picture.getViewCount()).orElse(0L);
        long likeCount = Optional.ofNullable(picture.getLikeCount()).orElse(0L);
        long commentCount = Optional.ofNullable(picture.getCommentCount()).orElse(0L);
        long shareCount = Optional.ofNullable(picture.getShareCount()).orElse(0L);

        // 分子 P
        double p = Math.log1p(viewCount * LOG_MULTIPLIER) * viewWeight +
                Math.log1p(likeCount * LOG_MULTIPLIER) * likeWeight +
                Math.log1p(commentCount * LOG_MULTIPLIER) * commentWeight +
                Math.log1p(shareCount * LOG_MULTIPLIER) * shareWeight;

        // 核心修复：加上基础分，让新图片拥有初始降落伞
        p += baseScore;

        // 分母 (T + 2)^G
        double timeDecay = Math.pow(hours + 2.0, gravity);

        // Hacker News 公式
        double finalScore = (p * SCORE_SCALE_FACTOR) / timeDecay;

        if (isBotUser(picture.getUserId())) {
            finalScore = finalScore * 0.3;
        }

        return Math.max(finalScore, MIN_SCORE);
    }

    private boolean isBotUser(Long userId) {
        if (userId == null) {
            return false;
        }
        try {
            com.lumenglover.yuemupicturebackend.model.entity.User user = userService.getById(userId);
            return user != null && user.getIsBot() != null && user.getIsBot() == 1;
        } catch (Exception e) {
            log.warn("查询用户isBot状态失败 | userId={}", userId, e);
            return false;
        }
    }

    private void batchUpdateScores(List<Picture> picturesToUpdate) {
        try {
            if (!pictureService.updateBatchRecommendScore(picturesToUpdate)) {
                log.error("批量更新失败，大小:{}", picturesToUpdate.size());
            }
        } catch (Exception e) {
            log.error("批量更新异常", e);
        }
    }

    private Double getCachedScore(Long pictureId) {
        String key = "picture:score:" + pictureId;
        String value = stringRedisTemplate.opsForValue().get(key);
        return value != null ? Double.parseDouble(value) : null;
    }

    private void cacheScoreIfNeeded(Picture picture, double score, Instant now) {
        long hours = ChronoUnit.HOURS.between(picture.getCreateTime().toInstant(), now);
        if (hours > CACHE_THRESHOLD_HOURS) {
            stringRedisTemplate.opsForValue().set("picture:score:" + picture.getId(),
                    String.valueOf(score), CACHE_EXPIRE_HOURS, TimeUnit.HOURS);
        }
    }
}
