package com.lumenglover.yuemupicturebackend.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Set;

/**
 * AI Token 每周限额重置任务
 */
@Component
@Slf4j
public class AiTokenResetJob {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String LIMIT_WEEK_PREFIX = "ai:limit:week:*";
    private static final String IMAGE_GEN_WEEK_PREFIX = "ai:image_gen:week:*";

    /**
     * 每周一 00:00:00 执行
     */
    @Scheduled(cron = "0 0 0 * * MON")
    public void resetWeeklyAiTokens() {
        log.info("开始清空本周 AI Token 消耗缓存数据");
        try {
            Set<String> keys = stringRedisTemplate.keys(LIMIT_WEEK_PREFIX);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("成功清空 {} 个用户的 AI Token 本周消耗数据", keys.size());
            } else {
                log.info("没有需要清空的 AI Token 周限额缓存数据");
            }

            Set<String> imageGenKeys = stringRedisTemplate.keys(IMAGE_GEN_WEEK_PREFIX);
            if (imageGenKeys != null && !imageGenKeys.isEmpty()) {
                stringRedisTemplate.delete(imageGenKeys);
                log.info("成功清空 {} 个用户的 AI 生图本周消耗数据", imageGenKeys.size());
            } else {
                log.info("没有需要清空的 AI 生图周限额缓存数据");
            }
        } catch (Exception e) {
            log.error("清空 AI Token 周消耗缓存异常", e);
        }
    }
}
