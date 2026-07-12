package com.lumenglover.yuemupicturebackend.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;

/**
 * 缓存清理定时任务
 * 定期清理过期或无用的缓存数据，防止内存占用持续增长
 */
@Component
@Slf4j
public class CacheCleanupJob {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 清理推荐系统相关缓存
     * 每小时执行一次
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanRecommendationCache() {
        try {
            log.info("开始清理推荐系统缓存...");

            // 清理帖子用户历史记录缓存（保留最近1小时的）
            Set<String> postHistoryKeys = stringRedisTemplate.keys("user:post:history:*");
            if (postHistoryKeys != null && !postHistoryKeys.isEmpty()) {
                int cleanedCount = 0;
                for (String key : postHistoryKeys) {
                    Long ttl = stringRedisTemplate.getExpire(key);
                    // 如果 TTL 小于 300 秒（5分钟），删除该缓存
                    if (ttl != null && ttl < 300) {
                        stringRedisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
                log.info("清理帖子用户历史记录缓存完成，共清理 {} 条", cleanedCount);
            }

            // 清理图片用户历史记录缓存
            Set<String> pictureHistoryKeys = stringRedisTemplate.keys("user:picture:history:*");
            if (pictureHistoryKeys != null && !pictureHistoryKeys.isEmpty()) {
                int cleanedCount = 0;
                for (String key : pictureHistoryKeys) {
                    Long ttl = stringRedisTemplate.getExpire(key);
                    if (ttl != null && ttl < 300) {
                        stringRedisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
                log.info("清理图片用户历史记录缓存完成，共清理 {} 条", cleanedCount);
            }

            // 清理帖子 CF 推荐缓存（保留最近30分钟的）
            Set<String> postCfKeys = stringRedisTemplate.keys("user:post:cf:*");
            if (postCfKeys != null && !postCfKeys.isEmpty()) {
                int cleanedCount = 0;
                for (String key : postCfKeys) {
                    Long ttl = stringRedisTemplate.getExpire(key);
                    if (ttl != null && ttl < 180) {
                        stringRedisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
                log.info("清理帖子 CF 推荐缓存完成，共清理 {} 条", cleanedCount);
            }

            // 清理图片 CF 推荐缓存
            Set<String> pictureCfKeys = stringRedisTemplate.keys("user:picture:cf:*");
            if (pictureCfKeys != null && !pictureCfKeys.isEmpty()) {
                int cleanedCount = 0;
                for (String key : pictureCfKeys) {
                    Long ttl = stringRedisTemplate.getExpire(key);
                    if (ttl != null && ttl < 180) {
                        stringRedisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
                log.info("清理图片 CF 推荐缓存完成，共清理 {} 条", cleanedCount);
            }

            log.info("推荐系统缓存清理完成");
        } catch (Exception e) {
            log.error("清理推荐系统缓存失败", e);
        }
    }

    /**
     * 清理标签分类缓存
     * 每天凌晨2点执行
     */
    @Scheduled(cron = "0 30 1 * * ?")
    public void cleanTagCategoryCache() {
        try {
            log.info("开始清理标签分类缓存...");

            // 清理用户个性化标签分类缓存
            Set<String> tagCategoryKeys = stringRedisTemplate.keys("post:tag_category:list:*");
            if (tagCategoryKeys != null && !tagCategoryKeys.isEmpty()) {
                stringRedisTemplate.delete(tagCategoryKeys);
                log.info("清理标签分类缓存完成，共清理 {} 条", tagCategoryKeys.size());
            }

            // 清理公共标签分类缓存
            stringRedisTemplate.delete("post:tag_category:list");

            log.info("标签分类缓存清理完成");
        } catch (Exception e) {
            log.error("清理标签分类缓存失败", e);
        }
    }

    /**
     * 清理浏览量计数缓存
     * 每30分钟执行一次
     */
    @Scheduled(cron = "0 */30 * * * ?")
    public void cleanViewCountCache() {
        try {
            log.info("开始清理浏览量计数缓存...");

            // 清理浏览量锁（超过10分钟的锁）
            Set<String> lockKeys = stringRedisTemplate.keys("post:viewCount:lock:*");
            if (lockKeys != null && !lockKeys.isEmpty()) {
                int cleanedCount = 0;
                for (String key : lockKeys) {
                    Long ttl = stringRedisTemplate.getExpire(key);
                    if (ttl != null && ttl < -1) {
                        stringRedisTemplate.delete(key);
                        cleanedCount++;
                    }
                }
                log.info("清理浏览量锁完成，共清理 {} 条", cleanedCount);
            }

            log.info("浏览量计数缓存清理完成");
        } catch (Exception e) {
            log.error("清理浏览量计数缓存失败", e);
        }
    }

    /**
     * 清理热门池缓存
     * 每15分钟执行一次
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void cleanHotPoolCache() {
        try {
            log.info("开始清理热门池缓存...");

            // 删除热门池缓存，让其重新构建
            stringRedisTemplate.delete("post:hot:pool");

            log.info("热门池缓存清理完成");
        } catch (Exception e) {
            log.error("清理热门池缓存失败", e);
        }
    }

    /**
     * 清理总数统计缓存
     * 每10分钟执行一次
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void cleanCountCache() {
        try {
            log.info("开始清理总数统计缓存...");

            // 删除总数缓存
            stringRedisTemplate.delete("post:public:total_count");

            log.info("总数统计缓存清理完成");
        } catch (Exception e) {
            log.error("清理总数统计缓存失败", e);
        }
    }
}
