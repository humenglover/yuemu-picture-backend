package com.lumenglover.yuemupicturebackend.controller;
import cn.dev33.satoken.annotation.SaCheckRole;

import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.job.PexelsCrawlerJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.Executor;

/**
 * Pexels 抓取控制器（用于测试）
 */
@RestController
@RequestMapping("/pexels")
@Slf4j
public class PexelsCrawlerController {

    @Resource
    private PexelsCrawlerJob pexelsCrawlerJob;

    @Resource
    @Qualifier("asyncExecutor")
    private Executor asyncExecutor;

    /**
     * 手动触发 Pexels 抓取任务
     */
    @SaCheckRole("admin")
    @PostMapping("/crawl/trigger")
    public BaseResponse<String> triggerCrawl() {
        log.info("🔧 收到手动触发 Pexels 抓取任务请求");

        try {
            // 使用线程池执行，避免裸 new Thread() 导致的线程泄漏
            asyncExecutor.execute(() -> {
                try {
                    pexelsCrawlerJob.manualCrawl();
                } catch (Exception e) {
                    log.error("❌ 手动抓取任务执行失败", e);
                }
            });

            return ResultUtils.success("抓取任务已启动，请查看日志");
        } catch (Exception e) {
            log.error("❌ 触发抓取任务失败", e);
            return (BaseResponse<String>) ResultUtils.error(500, "触发失败: " + e.getMessage());
        }
    }
}
