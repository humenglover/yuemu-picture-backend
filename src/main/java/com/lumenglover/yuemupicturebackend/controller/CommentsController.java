package com.lumenglover.yuemupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsDeleteRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsLikeRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsQueryRequest;
import com.lumenglover.yuemupicturebackend.model.vo.CommentsVO;
import com.lumenglover.yuemupicturebackend.model.vo.PictureVO;
import com.lumenglover.yuemupicturebackend.service.CommentsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping("/comments")
public class CommentsController {
    @Resource
    private CommentsService commentsService;

    /**
     * 查询指定图片id的评论
     */
    @PostMapping("/query")
    public BaseResponse<Page<CommentsVO>> queryComment(@RequestBody CommentsQueryRequest commentsQueryRequest, HttpServletRequest request) {
        return ResultUtils.success(commentsService.queryComment(commentsQueryRequest, request));
    }

    /**
     * 添加评论
     */
    @PostMapping("/add")
    public BaseResponse<Boolean>  addComment(@RequestBody CommentsAddRequest commentsAddRequest, HttpServletRequest request) {
        return ResultUtils.success(commentsService.addComment(commentsAddRequest, request));
    }

    /**
     * 删除评论
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteComment(@RequestBody CommentsDeleteRequest commentsDeleteRequest, HttpServletRequest request) {
        return ResultUtils.success(commentsService.deleteComment(commentsDeleteRequest, request));
    }

    /**
     * 喜欢评论内容
     */
    @PostMapping("/like")
    public BaseResponse<Boolean> likeComment(@RequestBody CommentsLikeRequest commentslikeRequest, HttpServletRequest request) {
        return ResultUtils.success(commentsService.likeComment(commentslikeRequest, request));
    }
}
