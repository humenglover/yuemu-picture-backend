package com.lumenglover.yuemupicturebackend.utils;

import com.lumenglover.yuemupicturebackend.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 帖子分数更新追踪器
 * 用于维护需要重新计算热榜分数的帖子ID列表
 */
@Slf4j
@Component
public class PostScoreUpdateTracker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 添加需要重新计算热榜分数的帖子ID
     * @param postId 帖子ID
     */
    public void addPostToHotScoreUpdateQueue(Long postId) {
        try {
            String key = RedisConstant.POST_HOT_SCORE_UPDATE_QUEUE_KEY;
            stringRedisTemplate.opsForSet().add(key, postId.toString());
            // 设置过期时间为24小时，避免队列无限增长
            stringRedisTemplate.expire(key, 24, TimeUnit.HOURS);
            log.debug("Added postId {} to hot score update queue", postId);
        } catch (Exception e) {
            log.error("Failed to add postId {} to hot score update queue", postId, e);
        }
    }

    /**
     * 获取需要重新计算热榜分数的帖子ID集合
     * @return 帖子ID集合
     */
    public Set<String> getPostIdsForHotScoreUpdate() {
        try {
            String key = RedisConstant.POST_HOT_SCORE_UPDATE_QUEUE_KEY;
            Set<String> result = stringRedisTemplate.opsForSet().members(key);
            return result != null ? result : Collections.emptySet();
        } catch (Exception e) {
            log.error("Failed to get post IDs from hot score update queue", e);
            return Collections.emptySet();
        }
    }

    /**
     * 从热榜分数更新队列中移除指定的帖子ID
     * @param postIds 帖子ID集合
     */
    public void removePostsFromHotScoreUpdateQueue(Set<String> postIds) {
        try {
            String key = RedisConstant.POST_HOT_SCORE_UPDATE_QUEUE_KEY;
            if (postIds != null && !postIds.isEmpty()) {
                stringRedisTemplate.opsForSet().remove(key, postIds.toArray());
                log.debug("Removed {} post IDs from hot score update queue", postIds.size());
            }
        } catch (Exception e) {
            log.error("Failed to remove post IDs from hot score update queue", e);
        }
    }

    /**
     * 获取热榜分数更新队列中帖子的数量
     * @return 队列中的帖子数量
     */
    public Long getHotScoreQueueSize() {
        try {
            String key = RedisConstant.POST_HOT_SCORE_UPDATE_QUEUE_KEY;
            return stringRedisTemplate.opsForSet().size(key);
        } catch (Exception e) {
            log.error("Failed to get hot score update queue size", e);
            return 0L;
        }
    }
}
