package com.lumenglover.yuemupicturebackend.controller;


import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.PageRequest;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.entity.Tag;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.model.vo.TagVO;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.service.TagService;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.PostMapping;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.RequestBody;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.RequestMapping;
import cn.dev33.satoken.annotation.SaCheckRole;
import org.springframework.web.bind.annotation.RestController;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import cn.dev33.satoken.annotation.SaCheckRole;
import javax.annotation.Resource;
import cn.dev33.satoken.annotation.SaCheckRole;
import java.util.List;

@RestController
@RequestMapping("/tag")
public class TagController {
    @Resource
    private TagService tagService;
    /**
     * 获取所有标签
     */
    @PostMapping("list/page/vo")
    @SaCheckRole("admin")
    public BaseResponse<Page<TagVO>> listTagVOByPage(@RequestBody PageRequest pageRequest){
        long current = pageRequest.getCurrent();
        long pageSize = pageRequest.getPageSize();
        Page<Tag> tagPage = tagService.page(new Page<>(current, pageSize));
        Page<TagVO> tagVOPage = new Page<>(current, pageSize,tagPage.getTotal());
        List<TagVO> tagVOList = tagService.listTagVOByPage(tagPage.getRecords());
        tagVOPage.setRecords(tagVOList);
        return ResultUtils.success(tagVOPage);
    }

    /**
     * 添加标签
     */
    @PostMapping("/add")
    @SaCheckRole("admin")
    public BaseResponse<Boolean> addTag(String tagName){
        ThrowUtils.throwIf(tagName == null || tagName.length() == 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(tagService.addTag(tagName));
    }

    /**
     * 删除标签
     */
    @PostMapping("/delete")
    @SaCheckRole("admin")
    @RateLimiter(key = "tag_delete", time = 60, count = 10, message = "标签删除过于频繁，请稍后再试")
    public BaseResponse<Boolean> deleteTag(Long id){
        ThrowUtils.throwIf(id == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(tagService.deleteTag(id));
    }

    /**
     * 查找标签
     */
    @PostMapping("/search")
    @SaCheckRole("admin")
    @RateLimiter(key = "tag_search", time = 60, count = 30, message = "标签搜索过于频繁，请稍后再试")
    public BaseResponse<List<TagVO>> searchTag(String tagName){
        ThrowUtils.throwIf(tagName == null || tagName.length() == 0, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(tagService.searchTag(tagName));
    }

}
