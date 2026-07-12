package com.lumenglover.yuemupicturebackend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Pexels 配置类
 */
@Configuration
@ConfigurationProperties(prefix = "pexels")
@Data
public class PexelsConfig {

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 功能开关
     */
    private Boolean enabled = false;

    /**
     * 定时任务 cron 表达式
     */
    private String cron;

    /**
     * API 基础 URL
     */
    private String baseUrl = "https://api.pexels.com/v1";

    /**
     * Pexels 网站 URL
     */
    private String websiteUrl = "https://www.pexels.com";

    /**
     * 每天成功上传的目标数量
     */
    private Integer dailyTarget = 20;

    /**
     * 每个分类抓取的数量
     */
    private Integer perCategory = 5;

    /**
     * 随机选择的分类数量
     */
    private Integer randomCategories = 5;

    /**
     * 批量处理数量
     */
    private Integer batchSize = 20;

    /**
     * 机器人配置
     */
    private BotConfig bot = new BotConfig();

    @Data
    public static class BotConfig {
        /**
         * 用户名（显示名称）
         */
        private String username = "图库助手";

        /**
         * 用户账号
         */
        private String userAccount = "pexels_bot";

        /**
         * 密码
         */
        private String password = "pexels_bot_password";

        /**
         * 用户角色
         */
        private String userRole = "bot";

        /**
         * 用户简介
         */
        private String userProfile = "我是图库助手，为您精选来自 Pexels 的优质图片素材。";

        /**
         * 头像URL
         */
        private String avatarUrl = "";
    }
}
