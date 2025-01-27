package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsDeleteRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsLikeRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Comments;
import com.lumenglover.yuemupicturebackend.model.vo.CommentsVO;

import javax.servlet.http.HttpServletRequest;

/**
* @author 鹿梦
* @description 针对表【comments】的数据库操作Service
* @createDate 2024-12-29 18:26:42
*/
public interface CommentsService extends IService<Comments> {

    Boolean addComment(CommentsAddRequest commentsAddRequest, HttpServletRequest request);


    Boolean deleteComment(CommentsDeleteRequest commentsDeleteRequest, HttpServletRequest request);


    Page<CommentsVO> queryComment(CommentsQueryRequest commentsQueryRequest, HttpServletRequest request);

    Boolean likeComment(CommentsLikeRequest commentslikeRequest, HttpServletRequest request);
}
