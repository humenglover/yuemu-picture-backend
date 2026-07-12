package com.lumenglover.yuemupicturebackend.job;

import com.lumenglover.yuemupicturebackend.service.AuthorRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 作者榜单定时任务
 */
@Component
@Slf4j
public class AuthorRankingJob implements ApplicationRunner {

    @Resource
    private AuthorRankingService authorRankingService;

    /**
     * 应用启动时执行一次榜单计算
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("应用启动：开始初始化作者榜单数据");
        try {
            // 异步执行，避免阻塞应用启动
            new Thread(() -> {
                try {
                    // 延迟5秒，等待其他服务初始化完成
                    Thread.sleep(5000);
                    authorRankingService.calculateAllRankings();
                    log.info("应用启动：作者榜单初始化完成");
                } catch (Exception e) {
                    log.error("应用启动：作者榜单初始化失败", e);
                }
            }).start();
        } catch (Exception e) {
            log.error("应用启动：启动榜单初始化线程失败", e);
        }
    }

    /**
     * 每小时更新一次作者榜单
     * 日榜、周榜、月榜、总榜
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void updateAuthorRankings() {
        log.info("开始执行作者榜单更新任务");
        try {
            authorRankingService.calculateAllRankings();
            log.info("作者榜单更新任务执行完成");
        } catch (Exception e) {
            log.error("作者榜单更新任务执行失败", e);
        }
    }

    /**
     * 每天凌晨2点更新总榜
     */
    @Scheduled(cron = "0 30 2 * * ?")
    public void updateTotalRankings() {
        log.info("开始执行作者总榜更新任务");
        try {
            authorRankingService.calculatePictureAuthorRanking("total");
            authorRankingService.calculatePostAuthorRanking("total");
            log.info("作者总榜更新任务执行完成");
        } catch (Exception e) {
            log.error("作者总榜更新任务执行失败", e);
        }
    }
}
