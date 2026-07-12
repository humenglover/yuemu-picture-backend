package com.lumenglover.yuemupicturebackend.api.pexels;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.api.pexels.model.PexelsPhoto;
import com.lumenglover.yuemupicturebackend.api.pexels.model.PexelsSearchResponse;
import com.lumenglover.yuemupicturebackend.config.PexelsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Pexels API 客户端
 */
@Component
@Slf4j
public class PexelsApiClient {

    @Resource
    private PexelsConfig pexelsConfig;

    /**
     * 搜索图片
     *
     * @param query   搜索关键词
     * @param page    页码
     * @param perPage 每页数量
     * @return 搜索响应
     */
    public PexelsSearchResponse search(String query, int page, int perPage) {
        if (StrUtil.isBlank(pexelsConfig.getApiKey())) {
            log.error("Pexels API Key 未配置");
            return null;
        }

        String url = String.format("%s/search?query=%s&page=%d&per_page=%d",
                pexelsConfig.getBaseUrl(), query, page, perPage);

        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", pexelsConfig.getApiKey())
                .timeout(10000)
                .execute()) {

            if (!response.isOk()) {
                log.error("Pexels API 调用失败: status={}, body={}", response.getStatus(), response.body());
                return null;
            }

            String body = response.body();
            return JSONUtil.toBean(body, PexelsSearchResponse.class);

        } catch (Exception e) {
            log.error("Pexels API 调用异常: query={}", query, e);
            return null;
        }
    }

    /**
     * 获取图片详情
     *
     * @param photoId 图片ID
     * @return 图片详情
     */
    public PexelsPhoto getPhotoById(Long photoId) {
        if (StrUtil.isBlank(pexelsConfig.getApiKey())) {
            log.error("Pexels API Key 未配置");
            return null;
        }

        String url = String.format("%s/photos/%d", pexelsConfig.getBaseUrl(), photoId);

        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", pexelsConfig.getApiKey())
                .timeout(10000)
                .execute()) {

            if (!response.isOk()) {
                log.error("获取 Pexels 图片详情失败: photoId={}, status={}", photoId, response.getStatus());
                return null;
            }

            String body = response.body();
            return JSONUtil.toBean(body, PexelsPhoto.class);

        } catch (Exception e) {
            log.error("获取 Pexels 图片详情异常: photoId={}", photoId, e);
            return null;
        }
    }

    /**
     * 获取精选图片
     *
     * @param page    页码
     * @param perPage 每页数量
     * @return 搜索响应
     */
    public PexelsSearchResponse getCurated(int page, int perPage) {
        if (StrUtil.isBlank(pexelsConfig.getApiKey())) {
            log.error("Pexels API Key 未配置");
            return null;
        }

        String url = String.format("%s/curated?page=%d&per_page=%d",
                pexelsConfig.getBaseUrl(), page, perPage);

        try (HttpResponse response = HttpRequest.get(url)
                .header("Authorization", pexelsConfig.getApiKey())
                .timeout(10000)
                .execute()) {

            if (!response.isOk()) {
                log.error("Pexels API 调用失败: status={}, body={}", response.getStatus(), response.body());
                return null;
            }

            String body = response.body();
            return JSONUtil.toBean(body, PexelsSearchResponse.class);

        } catch (Exception e) {
            log.error("Pexels API 调用异常", e);
            return null;
        }
    }
}
