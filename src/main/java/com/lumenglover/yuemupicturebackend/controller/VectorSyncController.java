package com.lumenglover.yuemupicturebackend.controller;

import com.lumenglover.yuemupicturebackend.annotation.AuthCheck;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.lumenglover.yuemupicturebackend.service.VectorSyncService;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 向量同步控制器 (全局单例任务)
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/sync")
public class VectorSyncController {

    // ================= 全局状态管理 =================

    /**
     * 存放所有订阅进度条的 SSE 客户端连接
     */
    private static final CopyOnWriteArrayList<SseEmitter> syncEmitters = new CopyOnWriteArrayList<>();

    /**
     * 全局锁：标记同步任务是否正在运行
     */
    public static final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * 进度状态
     */
    public static final AtomicInteger totalCount = new AtomicInteger(0);
    public static final AtomicInteger processedCount = new AtomicInteger(0);
    public static final AtomicInteger failedCount = new AtomicInteger(0);

    // ================= 心跳保活线程池 =================
    private static final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    private static ScheduledFuture<?> heartbeatTask;

    /**
     * 启动定时心跳，防止 SSE 连接断开
     */
    private static synchronized void startHeartbeatIfNeeded() {
        if (heartbeatTask == null || heartbeatTask.isCancelled()) {
            heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> {
                for (SseEmitter emitter : syncEmitters) {
                    try {
                        emitter.send(SseEmitter.event().comment("ping"));
                    } catch (Exception e) {
                        syncEmitters.remove(emitter);
                    }
                }
            }, 5, 5, TimeUnit.SECONDS);
        }
    }

    /**
     * 内部类：向前端下发的进度数据结构
     */
    @Data
    public static class SyncProgressDTO {
        private boolean running;
        private int total;
        private int processed;
        private int failed;

        public static SyncProgressDTO current() {
            SyncProgressDTO dto = new SyncProgressDTO();
            dto.setRunning(isRunning.get());
            dto.setTotal(totalCount.get());
            dto.setProcessed(processedCount.get());
            dto.setFailed(failedCount.get());
            return dto;
        }
    }

    // ================= 接口实现 =================

    /**
     * SSE 进度流订阅接口
     * 前端通过 new EventSource("/api/vector/sync/progress/stream") 连接
     */
    @GetMapping(value = "/progress/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public SseEmitter getSyncProgressStream(HttpServletResponse response) {
        // 设置响应头，防止 Nginx 或浏览器缓存导致 SSE 失效
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setCharacterEncoding("UTF-8");

        // 设定超时时间为 30 分钟 (1800000L)
        SseEmitter emitter = new SseEmitter(1800000L);
        syncEmitters.add(emitter);

        startHeartbeatIfNeeded();

        // 注册回调，清理失效连接
        emitter.onCompletion(() -> syncEmitters.remove(emitter));
        emitter.onTimeout(() -> {
            syncEmitters.remove(emitter);
            emitter.complete();
        });
        emitter.onError((ex) -> syncEmitters.remove(emitter));

        // 立刻给刚连上的前端推送一次当前进度（防刷新网页）
        try {
            emitter.send(SseEmitter.event().name("syncProgress").data(SyncProgressDTO.current()));
        } catch (IOException e) {
            syncEmitters.remove(emitter);
        }

        return emitter;
    }

    @Resource
    private VectorSyncService vectorSyncService;

    /**
     * 触发全量同步任务
     */
    @PostMapping("/start")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> startSync() {
        // 1. 获取全局锁，防止多个管理员同时点击
        if (!isRunning.compareAndSet(false, true)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "全量同步任务正在进行中，请勿重复触发！");
        }

        log.info("管理员触发了全局向量化同步任务");

        // 2. 初始化进度条状态
        totalCount.set(0);
        processedCount.set(0);
        failedCount.set(0);

        // 立刻通知所有在线的网页，进度条归零，开始运行
        distributeProgress();

        // 3. 调用 VectorSyncService 的 @Async 方法真正开始跑批
        vectorSyncService.runFullSyncAsync();

        return ResultUtils.success(true);
    }

    // ================= 工具方法 (提供给 Service 回调使用) =================

    /**
     * 广播最新进度给所有客户端
     * 供后台 Service 在 for 循环中每处理完一张图时调用
     */
    public static void distributeProgress() {
        SyncProgressDTO currentData = SyncProgressDTO.current();
        for (SseEmitter emitter : syncEmitters) {
            try {
                emitter.send(SseEmitter.event().name("syncProgress").data(currentData));
            } catch (Exception e) {
                syncEmitters.remove(emitter);
            }
        }
    }

    /**
     * 广播任务完成并释放锁
     * 供后台 Service 在 finally 块中调用
     */
    public static void finishSync() {
        isRunning.set(false);
        for (SseEmitter emitter : syncEmitters) {
            try {
                emitter.send(SseEmitter.event().name("syncDone").data("同步完成"));
            } catch (Exception e) {
                // 忽略
            } finally {
                emitter.complete(); // 主动关闭长连接
            }
        }
        syncEmitters.clear();
    }
}
