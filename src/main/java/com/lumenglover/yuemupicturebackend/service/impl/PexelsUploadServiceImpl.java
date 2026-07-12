package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.api.pexels.PexelsApiClient;
import com.lumenglover.yuemupicturebackend.api.pexels.model.PexelsPhoto;
import com.lumenglover.yuemupicturebackend.service.PythonRagService;
import com.lumenglover.yuemupicturebackend.model.dto.rag.PythonRagResponse;
import com.lumenglover.yuemupicturebackend.config.PexelsConfig;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.model.dto.picture.PictureUploadRequest;
import com.lumenglover.yuemupicturebackend.model.entity.PexelsCrawlRecord;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.PictureVO;
import com.lumenglover.yuemupicturebackend.service.PexelsCrawlRecordService;
import com.lumenglover.yuemupicturebackend.service.PexelsUploadService;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Pexels 图片上传服务实现
 */
@Service
@Slf4j
public class PexelsUploadServiceImpl implements PexelsUploadService {

    @Resource
    private PexelsCrawlRecordService crawlRecordService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private PexelsApiClient pexelsApiClient;

    @Resource
    private PexelsConfig pexelsConfig;

    @Resource
    private PythonRagService pythonRagService;

    @Override
    public void processPendingUploads() {
        // 1. 获取机器人用户
        User botUser = getBotUser();
        if (botUser == null) {
            log.error("❌ 未找到机器人用户，无法上传图片");
            return;
        }

        // 2. 查询待上传的记录（限制为每日目标数量）
        List<PexelsCrawlRecord> pendingRecords = crawlRecordService.list(
                new QueryWrapper<PexelsCrawlRecord>()
                        .eq("uploadStatus", 0)
                        .lt("retryCount", 3) // 重试次数小于3次
                        .last("LIMIT " + pexelsConfig.getDailyTarget())
        );

        if (pendingRecords.isEmpty()) {
            log.info("✅ 没有待上传的图片");
            return;
        }

        log.info("📤 开始处理待上传图片，数量: {} (目标: {})",
                pendingRecords.size(), pexelsConfig.getDailyTarget());

        // 3. 批量获取图片详情并提取英文标题
        List<PexelsPhoto> photos = new ArrayList<>();
        List<String> englishTitles = new ArrayList<>();

        for (PexelsCrawlRecord record : pendingRecords) {
            try {
                PexelsPhoto photo = pexelsApiClient.getPhotoById(record.getPexelsPhotoId());
                if (photo != null) {
                    photos.add(photo);
                    String alt = photo.getAlt();
                    String rawTitle = StrUtil.isBlank(alt) ? "Pexels 精选图片" :
                            (alt.length() > 100 ? alt.substring(0, 100) : alt);
                    englishTitles.add(rawTitle);
                }
            } catch (Exception e) {
                log.warn("⚠️ 获取图片详情失败: pexelsPhotoId={}", record.getPexelsPhotoId());
                photos.add(null);
                englishTitles.add("Pexels 精选图片");
            }
        }

        // 4. 批量翻译所有标题（一次 AI 调用）
        log.info("🌐 开始批量翻译 {} 个标题", englishTitles.size());
        List<String> chineseTitles = batchTranslateToChineseWithAI(englishTitles);

        int successCount = 0;
        int failCount = 0;

        // 5. 逐个上传，使用翻译后的标题
        for (int i = 0; i < pendingRecords.size(); i++) {
            // 检查是否已达到目标
            if (successCount >= pexelsConfig.getDailyTarget()) {
                log.info("✅ 已达到每日上传目标 {} 张，停止上传", pexelsConfig.getDailyTarget());
                break;
            }

            PexelsCrawlRecord record = pendingRecords.get(i);
            PexelsPhoto photo = photos.get(i);
            String chineseTitle = chineseTitles.get(i);

            if (photo == null) {
                failCount++;
                handleUploadFailure(record, "获取图片详情失败");
                continue;
            }

            try {
                uploadSinglePictureWithTitle(record, photo, chineseTitle, botUser);
                successCount++;
                Thread.sleep(500); // 限流
            } catch (Exception e) {
                failCount++;
                log.error("❌ 上传图片失败: pexelsPhotoId={}", record.getPexelsPhotoId(), e);
                handleUploadFailure(record, e.getMessage());
            }
        }

        log.info("✅ 图片上传完成，成功: {}/{}, 失败: {}",
                successCount, pexelsConfig.getDailyTarget(), failCount);
    }

    /**
     * 上传单张图片（使用已翻译的标题）
     */
    private void uploadSinglePictureWithTitle(PexelsCrawlRecord record, PexelsPhoto photo,
                                              String chineseTitle, User botUser) {
        // 1. 构造上传请求
        PictureUploadRequest uploadRequest = new PictureUploadRequest();
        // 使用 large 尺寸图片（而非原图），减少文件大小，提升前端加载速度
        // large 尺寸通常为 1280px 宽度，适合网页展示，同时保持较好的清晰度
        uploadRequest.setFileUrl(photo.getSrc().getLarge());
        uploadRequest.setPicName(chineseTitle); // 使用翻译后的中文标题
        uploadRequest.setIntroduction(buildIntroduction(record)); // 构建简介

        // 2. 设置分类名称（从数据库查询）
        if (record.getCategoryId() != null) {
            uploadRequest.setCategoryName(record.getQueryKeyword());
        }

        // 3. 提取标签（从 alt 文本，转换为 JSON 数组格式）
        String tags = extractTagsAsString(photo.getAlt());
        if (StrUtil.isNotBlank(tags) && !tags.equals("[]")) {
            uploadRequest.setTagName(tags);
        }

        // 4. 设置图片元数据（避免重复下载）
        uploadRequest.setPicWidth(photo.getWidth());
        uploadRequest.setPicHeight(photo.getHeight());
        uploadRequest.setPicFormat("jpeg"); // Pexels 默认格式

        // 计算宽高比
        if (photo.getWidth() != null && photo.getHeight() != null && photo.getHeight() > 0) {
            double scale = (double) photo.getWidth() / photo.getHeight();
            uploadRequest.setPicScale(scale);
        }

        // 5. 🔥 使用专门的机器人上传方法
        //    - 直接设置为非草稿状态（isDraft=0）
        //    - 自动进行腾讯云图片审核
        //    - 审核通过后自动设置 reviewStatus=1
        PictureVO pictureVO = pictureService.uploadPictureByBot(uploadRequest, botUser);

        // 6. 更新抓取记录
        record.setUploadStatus(1); // 已上传
        record.setPictureId(pictureVO.getId());
        record.setUpdateTime(new Date());
        crawlRecordService.updateById(record);

        log.info("✅ 图片上传成功 | pexelsPhotoId={} | pictureId={} | 标题={} | 审核状态={} | 草稿状态={}",
                record.getPexelsPhotoId(), pictureVO.getId(), chineseTitle,
                pictureVO.getReviewStatus(), pictureVO.getIsDraft());
    }

    /**
     * 构建图片简介
     */
    private String buildIntroduction(PexelsCrawlRecord record) {
        return String.format(
                "本图片来自 %s 免费图库，由摄影师 %s 发布。\n" +
                        "摄影师主页: %s\n" +
                        "原图链接: %s",
                pexelsConfig.getWebsiteUrl(),
                record.getPhotographer(),
                record.getPhotographerUrl(),
                record.getPexelsUrl()
        );
    }

    /**
     * 批量翻译为中文（一次 AI 调用）
     */
    private List<String> batchTranslateToChineseWithAI(List<String> englishTitles) {
        if (englishTitles.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            // 构建批量翻译提示词
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请将以下英文图片描述翻译成简洁的中文标题（每个不超过30个字）。\n\n");
            promptBuilder.append("要求：\n");
            promptBuilder.append("1. 每行一个翻译结果，按顺序对应\n");
            promptBuilder.append("2. 只返回翻译后的中文标题，不要序号、引号或其他内容\n");
            promptBuilder.append("3. 保持简洁、准确、自然\n");
            promptBuilder.append("4. 适合作为图片标题\n\n");
            promptBuilder.append("英文描述列表：\n");

            for (int i = 0; i < englishTitles.size(); i++) {
                promptBuilder.append(i + 1).append(". ").append(englishTitles.get(i)).append("\n");
            }

            // 调用 Python AI 服务（使用纯 LLM 接口，避免泄露知识库）
            PythonRagResponse responseObj = pythonRagService.callPythonPureLLM(promptBuilder.toString(), null, 0.3);
            String response = responseObj.getAnswer();

            // 解析响应，按行分割
            String[] lines = response.trim().split("\n");
            List<String> chineseTitles = new ArrayList<>();

            for (String line : lines) {
                // 清理每行（去除序号、引号、空格等）
                String cleaned = line.trim()
                        .replaceAll("^\\d+[\\.、]\\s*", "") // 去除序号
                        .replaceAll("^[\"']|[\"']$", "")   // 去除引号
                        .trim();

                if (StrUtil.isNotBlank(cleaned)) {
                    // 限制长度
                    String title = cleaned.length() > 50 ? cleaned.substring(0, 50) : cleaned;
                    chineseTitles.add(title);
                }
            }

            // 如果翻译结果数量不匹配，用原标题补齐
            while (chineseTitles.size() < englishTitles.size()) {
                int index = chineseTitles.size();
                String fallback = englishTitles.get(index);
                chineseTitles.add(fallback.length() > 50 ? fallback.substring(0, 50) : fallback);
            }

            log.info("✅ 批量翻译成功，翻译了 {} 个标题", chineseTitles.size());
            return chineseTitles;

        } catch (Exception e) {
            log.error("❌ 批量翻译失败，使用原标题", e);
            // 翻译失败，返回原标题
            return englishTitles.stream()
                    .map(title -> title.length() > 50 ? title.substring(0, 50) : title)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 提取标签（返回 JSON 数组格式的字符串）
     */
    private String extractTagsAsString(String alt) {
        if (StrUtil.isBlank(alt)) {
            return "[]";
        }
        // 简单分词提取关键词
        String[] words = alt.toLowerCase().split("[\\s,]+");
        List<String> tags = Arrays.stream(words)
                .filter(w -> w.length() > 2)
                .limit(5)
                .collect(Collectors.toList());

        // 返回 JSON 数组格式
        return cn.hutool.json.JSONUtil.toJsonStr(tags);
    }

    /**
     * 处理上传失败
     */
    private void handleUploadFailure(PexelsCrawlRecord record, String errorMsg) {
        record.setRetryCount(record.getRetryCount() + 1);
        record.setErrorMessage(errorMsg);

        // 重试次数超过3次，标记为失败
        if (record.getRetryCount() >= 3) {
            record.setUploadStatus(2); // 失败
        }

        crawlRecordService.updateById(record);
    }

    /**
     * 获取机器人用户（仅通过 userAccount 唯一标识）
     */
    private User getBotUser() {
        return userService.getOne(
                new QueryWrapper<User>()
                        .eq("userAccount", pexelsConfig.getBot().getUserAccount())
        );
    }
}
