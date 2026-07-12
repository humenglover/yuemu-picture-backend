package com.lumenglover.yuemupicturebackend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.Post;
import com.lumenglover.yuemupicturebackend.service.PictureService;
import com.lumenglover.yuemupicturebackend.service.PostService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/seo")
@Slf4j
public class SeoController {

    @Resource
    private PictureService pictureService;

    @Resource
    private PostService postService;

    @Data
    public static class SeoDailyNewResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<Long> pictures = new ArrayList<>();
        private List<Long> posts = new ArrayList<>();
    }

    /**
     * 获取最近24小时新增的内容ID，供外部定时推送脚本使用
     *
     * @return 包含图片、帖子最新ID的集合
     */
    @GetMapping("/daily-new")
    @RateLimiter(key = "seo_daily_new", time = 60, count = 10, message = "获取数据过于频繁，请稍后再试")
    public BaseResponse<SeoDailyNewResponse> getDailyNewContentIds() {
        // 获取24小时前的时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date yesterday = calendar.getTime();

        SeoDailyNewResponse response = new SeoDailyNewResponse();

        try {
            // 1. 获取最新图片 (公共、非草稿、未删除)
            QueryWrapper<Picture> pictureQueryWrapper = new QueryWrapper<>();
            pictureQueryWrapper.ge("createTime", yesterday);
            pictureQueryWrapper.eq("isDelete", 0);
            pictureQueryWrapper.eq("isDraft", 0);
            pictureQueryWrapper.isNull("spaceId"); // 必须是公共空间的图片
            pictureQueryWrapper.last("limit 200"); // 提取够每天的量，具体截断在脚本里做
            List<Long> pictureIds = pictureService.list(pictureQueryWrapper)
                    .stream()
                    .map(Picture::getId)
                    .collect(Collectors.toList());
            response.setPictures(pictureIds);

            // 2. 获取最新帖子 (已发布、非草稿、未删除)
            QueryWrapper<Post> postQueryWrapper = new QueryWrapper<>();
            postQueryWrapper.ge("createTime", yesterday);
            postQueryWrapper.eq("isDelete", 0);
            postQueryWrapper.eq("status", 1); // 1表示已发布/审核通过
            postQueryWrapper.eq("isDraft", 0);
            postQueryWrapper.last("limit 200");
            List<Long> postIds = postService.list(postQueryWrapper)
                    .stream()
                    .map(Post::getId)
                    .collect(Collectors.toList());
            response.setPosts(postIds);

        } catch (Exception e) {
            log.error("获取每日最新内容ID失败", e);
            // 忽略报错，返回空结果
        }

        return ResultUtils.success(response);
    }
}
