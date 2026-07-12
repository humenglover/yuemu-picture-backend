package com.lumenglover.yuemupicturebackend.job;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.api.pexels.PexelsApiClient;
import com.lumenglover.yuemupicturebackend.api.pexels.model.PexelsPhoto;
import com.lumenglover.yuemupicturebackend.api.pexels.model.PexelsSearchResponse;
import com.lumenglover.yuemupicturebackend.config.PexelsConfig;
import com.lumenglover.yuemupicturebackend.model.entity.Category;
import com.lumenglover.yuemupicturebackend.model.entity.PexelsCrawlRecord;
import com.lumenglover.yuemupicturebackend.service.CategoryService;
import com.lumenglover.yuemupicturebackend.service.PexelsCrawlRecordService;
import com.lumenglover.yuemupicturebackend.service.PexelsUploadService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Pexels 图片抓取定时任务
 */
@Component
@Slf4j
@EnableScheduling
public class PexelsCrawlerJob {

    @Resource
    private PexelsConfig pexelsConfig;

    @Resource
    private CategoryService categoryService;

    @Resource
    private PexelsApiClient pexelsApiClient;

    @Resource
    private PexelsCrawlRecordService crawlRecordService;

    @Resource
    private PexelsUploadService uploadService;

    /**
     * 定时抓取任务
     */
    @Scheduled(cron = "${pexels.cron:0 0 1 * * ?}")
    public void crawlPexelsPictures() {
        if (!pexelsConfig.getEnabled()) {
            return;
        }

        log.info("🚀 开始执行 Pexels 图片抓取任务，目标: {} 张", pexelsConfig.getDailyTarget());

        try {
            // 1. 从数据库查询所有分类
            List<Category> allCategories = categoryService.list(
                    new QueryWrapper<Category>()
                            .eq("isDelete", 0)
                            .eq("type", 0) // 0-图片分类
            );

            if (allCategories.isEmpty()) {
                log.warn("⚠️ 未找到可用的分类，跳过抓取");
                return;
            }

            // 2. 随机打乱分类顺序
            Collections.shuffle(allCategories);

            // 3. 选择指定数量的随机分类
            int categoriesToCrawl = Math.min(pexelsConfig.getRandomCategories(), allCategories.size());
            List<Category> selectedCategories = allCategories.subList(0, categoriesToCrawl);

            log.info("📋 从 {} 个分类中随机选择了 {} 个分类进行抓取",
                    allCategories.size(), selectedCategories.size());

            int totalCrawled = 0;
            int targetReached = 0;

            // 4. 遍历选中的分类进行抓取
            for (Category category : selectedCategories) {
                // 检查是否已达到目标数量
                if (targetReached >= pexelsConfig.getDailyTarget()) {
                    log.info("✅ 已达到每日目标 {} 张，停止抓取", pexelsConfig.getDailyTarget());
                    break;
                }

                String keyword = category.getCategoryName();
                Long categoryId = category.getId();

                log.info("📥 开始抓取分类: {} (ID: {})", keyword, categoryId);

                // 5. 调用 Pexels API 搜索
                int crawledCount = crawlByKeyword(keyword, categoryId);
                totalCrawled += crawledCount;
                targetReached += crawledCount;

                // 6. 限流：每个关键词之间间隔1秒
                Thread.sleep(1000);
            }

            log.info("✅ Pexels 抓取任务完成，共抓取 {} 张图片", totalCrawled);

            // 7. 批量上传待处理的图片（限制数量）
            uploadService.processPendingUploads();

        } catch (Exception e) {
            log.error("❌ Pexels 抓取任务执行失败", e);
        }
    }

    /**
     * 根据关键词抓取图片
     */
    private int crawlByKeyword(String keyword, Long categoryId) {
        try {
            // 调用 Pexels API
            PexelsSearchResponse response = pexelsApiClient.search(
                    keyword,
                    1, // 页码
                    pexelsConfig.getPerCategory() // 每个分类抓取的数量
            );

            if (response == null || response.getPhotos() == null) {
                log.warn("⚠️ 关键词 {} 未返回结果", keyword);
                return 0;
            }

            int crawledCount = 0;

            // 保存抓取记录
            for (PexelsPhoto photo : response.getPhotos()) {
                // 检查是否已存在
                if (crawlRecordService.existsByPexelsPhotoId(photo.getId())) {
                    continue;
                }

                // 创建抓取记录
                PexelsCrawlRecord record = new PexelsCrawlRecord();
                record.setPexelsPhotoId(photo.getId());
                record.setPexelsUrl(photo.getUrl());
                record.setPhotographer(photo.getPhotographer());
                record.setPhotographerUrl(photo.getPhotographerUrl());
                record.setPhotographerId(photo.getPhotographerId());
                record.setQueryKeyword(keyword);
                record.setCategoryId(categoryId);
                record.setPageNumber(1);
                record.setCrawlTime(new Date());
                record.setUploadStatus(0); // 待上传
                record.setRetryCount(0);

                crawlRecordService.save(record);
                crawledCount++;
            }

            if (crawledCount > 0) {
                log.info("✅ 关键词 {} 抓取成功，新增 {} 张图片", keyword, crawledCount);
            }

            return crawledCount;

        } catch (Exception e) {
            log.error("❌ 抓取关键词 {} 失败", keyword, e);
            return 0;
        }
    }

    /**
     * 手动触发抓取任务（用于测试）
     */
    public void manualCrawl() {
        log.info("🔧 手动触发 Pexels 抓取任务");
        crawlPexelsPictures();
    }
}
