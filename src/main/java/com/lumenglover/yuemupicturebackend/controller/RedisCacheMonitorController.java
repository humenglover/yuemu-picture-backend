package com.lumenglover.yuemupicturebackend.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.RedisCacheMonitorService;
import cn.dev33.satoken.annotation.SaCheckRole;
import lombok.extern.slf4j.Slf4j;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.GetMapping;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.RequestMapping;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckRole;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckRole;
import java.util.Map;

/**
 * Redis缓存监控Controller
 */
@RestController
@RequestMapping("/monitor/cache")
@Slf4j
public class RedisCacheMonitorController {

    @Resource
    private RedisCacheMonitorService redisCacheMonitorService;

    /**
     * 获取Redis监控信息
     */
    @GetMapping("/info")
    @SaCheckRole("admin")
    @RateLimiter(key = "redis_cache_info", time = 60, count = 10, message = "Redis缓存信息查询过于频繁，请稍后再试")
    public BaseResponse<Map<String, Object>> getRedisInfo() {
        Map<String, Object> redisInfo = redisCacheMonitorService.getRedisMonitorInfo();
        return ResultUtils.success(redisInfo);
    }

    /**
     * 获取Redis内存使用信息
     */
    @GetMapping("/memory")
    @SaCheckRole("admin")
    @RateLimiter(key = "redis_cache_memory", time = 60, count = 10, message = "Redis内存信息查询过于频繁，请稍后再试")
    public BaseResponse<Map<String, Object>> getMemoryInfo() {
        Map<String, Object> memoryInfo = redisCacheMonitorService.getMemoryInfo();
        return ResultUtils.success(memoryInfo);
    }

    /**
     * 获取Redis键值统计信息
     */
    @GetMapping("/keys")
    @SaCheckRole("admin")
    @RateLimiter(key = "redis_cache_keys", time = 60, count = 10, message = "Redis键值统计查询过于频繁，请稍后再试")
    public BaseResponse<Map<String, Object>> getKeysStatistics() {
        Map<String, Object> keysStats = redisCacheMonitorService.getKeysStatistics();
        return ResultUtils.success(keysStats);
    }
}
