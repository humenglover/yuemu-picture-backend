package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.mapper.AiResourceMapper;
import com.lumenglover.yuemupicturebackend.model.entity.AiResource;
import com.lumenglover.yuemupicturebackend.service.AiResourceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
* @author lumenglover
* @description 针对表【ai_resource(AI资源库表)】的数据库操作Service实现
* @createDate 2024-05-23
*/
@Service
@Slf4j
public class AiResourceServiceImpl extends ServiceImpl<AiResourceMapper, AiResource>
    implements AiResourceService {

    // 正则表达式匹配 [附图: url] 和 [语音: url]
    private static final Pattern IMAGE_PATTERN = Pattern.compile("\\[附图:\\s*(https?://[^\\s\\]]+)\\]");
    private static final Pattern AUDIO_PATTERN = Pattern.compile("\\[语音:\\s*(https?://[^\\s\\]]+)\\]");

    @Override
    public void extractAndSaveResources(String content, Long messageId, Long userId) {
        if (StrUtil.isBlank(content) || userId == null) {
            return;
        }

        List<AiResource> resources = new ArrayList<>();

        // 匹配图片
        Matcher imgMatcher = IMAGE_PATTERN.matcher(content);
        while (imgMatcher.find()) {
            String url = imgMatcher.group(1);
            AiResource resource = new AiResource();
            resource.setUserId(userId);
            resource.setMessageId(messageId);
            resource.setResourceType("image");
            resource.setResourceUrl(url);
            resources.add(resource);
        }

        // 匹配音频
        Matcher audioMatcher = AUDIO_PATTERN.matcher(content);
        while (audioMatcher.find()) {
            String url = audioMatcher.group(1);
            AiResource resource = new AiResource();
            resource.setUserId(userId);
            resource.setMessageId(messageId);
            resource.setResourceType("audio");
            resource.setResourceUrl(url);
            resources.add(resource);
        }

        // 批量保存
        if (CollUtil.isNotEmpty(resources)) {
            try {
                this.saveBatch(resources);
                log.info("成功批量保存 AI 资源，数量：{}，消息ID：{}", resources.size(), messageId);
            } catch (Exception e) {
                log.error("保存 AI 资源失败", e);
            }
        }
    }

    @Override
    public List<AiResource> getResourcesByMessageId(Long messageId) {
        if (messageId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<AiResource> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("messageId", messageId);
        return this.list(queryWrapper);
    }
}
