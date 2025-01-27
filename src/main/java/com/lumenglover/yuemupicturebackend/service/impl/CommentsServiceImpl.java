package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.plugins.pagination.PageDTO;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.mapper.CommentsMapper;
import com.lumenglover.yuemupicturebackend.mapper.PictureMapper;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsDeleteRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsLikeRequest;
import com.lumenglover.yuemupicturebackend.model.dto.comments.CommentsQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Comments;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.CommentUserVO;
import com.lumenglover.yuemupicturebackend.model.vo.CommentsVO;
import com.lumenglover.yuemupicturebackend.service.CommentsService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import com.lumenglover.yuemupicturebackend.model.dto.es.EsPictureDao;
import com.lumenglover.yuemupicturebackend.model.entity.es.EsPicture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CommentsServiceImpl extends ServiceImpl<CommentsMapper, Comments> implements CommentsService {
    @Resource
    private UserService userService;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private EsPictureDao esPictureDao;  // 注入 ES DAO

    @Override
    public Boolean addComment(CommentsAddRequest commentsAddRequest, HttpServletRequest request) {
        // 判断是否登录
        User user = userService.getLoginUser(request);
        if (user == null) {
            return false;
        }
        Comments comments = new Comments();
        BeanUtils.copyProperties(commentsAddRequest, comments);
        save(comments);

        // 更新 MySQL 评论数
        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", commentsAddRequest.getPictureId());
        updateWrapper.setSql("commentCount = commentCount + 1");
        pictureMapper.update(null, updateWrapper);

        // 更新 ES 评论数
        updateEsPictureCommentCount(commentsAddRequest.getPictureId(), 1);

        return true;
    }

    @Override
    public Boolean deleteComment(CommentsDeleteRequest commentsDeleteRequest, HttpServletRequest request) {
        // 判断是否登录
        User user = userService.getLoginUser(request);
        if (user == null) {
            return false;
        }

        // 先计算要删除的评论及其子评论的总数
        int deletedCommentCount = countCommentsRecursively(commentsDeleteRequest.getCommentId());

        // 删除本人评论及子评论
        deleteCommentsRecursively(commentsDeleteRequest.getCommentId());

        // 更新 MySQL 评论数
        UpdateWrapper<Picture> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", commentsDeleteRequest.getPictureId())
                .setSql("commentCount = commentCount - " + deletedCommentCount)
                // 确保评论数不会小于0
                .ge("commentCount", deletedCommentCount);
        pictureMapper.update(null, updateWrapper);

        // 更新 ES 评论数
        try {
            Optional<EsPicture> esOptional = esPictureDao.findById(commentsDeleteRequest.getPictureId());
            if (esOptional.isPresent()) {
                EsPicture esPicture = esOptional.get();
                long newCount = Math.max(0, esPicture.getCommentCount() - deletedCommentCount);
                esPicture.setCommentCount(newCount);
                esPictureDao.save(esPicture);
            } else {
                // 如果 ES 中不存在，从 MySQL 获取最新数据
                Picture picture = pictureMapper.selectById(commentsDeleteRequest.getPictureId());
                if (picture != null) {
                    EsPicture esPicture = new EsPicture();
                    BeanUtils.copyProperties(picture, esPicture);
                    esPictureDao.save(esPicture);
                }
            }
        } catch (Exception e) {
            log.error("Failed to update ES comment count during deletion, pictureId: {}, deletedCount: {}",
                    commentsDeleteRequest.getPictureId(), deletedCommentCount, e);
        }

        return true;
    }

    private int countCommentsRecursively(Long commentId) {
        int count = 1; // 初始化为 1，包含当前评论

        QueryWrapper<Comments> childQueryWrapper = new QueryWrapper<>();
        childQueryWrapper.eq("parentCommentId", commentId);
        List<Comments> childComments = list(childQueryWrapper);

        for (Comments childComment : childComments) {
            count += countCommentsRecursively(childComment.getCommentId());
        }

        return count;
    }

    private void deleteCommentsRecursively(Long commentId) {
        // 查找子评论
        QueryWrapper<Comments> childQueryWrapper = new QueryWrapper<>();
        childQueryWrapper.eq("parentCommentId", commentId);
        List<Comments> childComments = list(childQueryWrapper);

        // 先递归删除子评论
        for (Comments childComment : childComments) {
            deleteCommentsRecursively(childComment.getCommentId());
        }

        // 最后删除当前评论
        QueryWrapper<Comments> deleteQueryWrapper = new QueryWrapper<>();
        deleteQueryWrapper.eq("commentId", commentId);
        remove(deleteQueryWrapper);
    }

    @Override
    public Page<CommentsVO> queryComment(CommentsQueryRequest commentsQueryRequest, HttpServletRequest request) {
        // 判断是否登录
        User user = userService.getLoginUser(request);
        if (user == null) {
            return null;
        }
        long current = commentsQueryRequest.getCurrent();
        long size = commentsQueryRequest.getPageSize();
        Page<Comments> page = new Page<>(current, size);
        //判断是否传递图片id
        ThrowUtils.throwIf(commentsQueryRequest.getPictureId()==null, ErrorCode.PARAMS_ERROR, "图片id不能为空");
        // 查询顶级评论
        QueryWrapper<Comments> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parentCommentId", 0).eq("pictureId", commentsQueryRequest.getPictureId());
        // 按照创建时间倒序排列
        queryWrapper.orderByDesc("createTime");
        // 查询评论是否存在，不存在返回空
        if (count(queryWrapper) == 0) {
            return null;
        }
        // 得到顶级评论列表
        Page<Comments> commentsPage = page(page, queryWrapper);
        // 获取评论用户的 ID 列表
        List<Long> userIds = commentsPage.getRecords().stream()
                .map(Comments::getUserId)
                .collect(Collectors.toList());
        // 批量查询评论用户信息，先检查 userIds 不为空
        if (userIds.isEmpty()) {
            return new PageDTO<>(commentsPage.getCurrent(), commentsPage.getSize(), commentsPage.getTotal());
        }
        // 批量查询评论用户信息
        List<User> users = userService.listByIds(userIds);
        // 将 User 列表转换为 commentUserVO 列表
        List<CommentUserVO> commentUserVOs = users.stream().map(user1 -> {
            CommentUserVO commentUserVO = new CommentUserVO();
            BeanUtils.copyProperties(user1, commentUserVO);
            return commentUserVO;
        }).collect(Collectors.toList());
        // 将 Comments 列表转换为 CommentsVO 列表
        Map<Long, CommentUserVO> userMap = commentUserVOs.stream()
                .collect(Collectors.toMap(CommentUserVO::getId, CommentUserVO -> CommentUserVO));
        List<CommentsVO> commentsVOList = commentsPage.getRecords().stream().map(comments -> {
            CommentsVO commentsVO = new CommentsVO();
            BeanUtils.copyProperties(comments, commentsVO);
            // 查找对应的评论用户信息
            CommentUserVO commentUserVO = userMap.get(comments.getUserId());
            if (commentUserVO!= null) {
                commentsVO.setCommentUser(commentUserVO);
            }
            // 递归查询子评论
            commentsVO.setChildren(getChildrenComments(comments.getCommentId()));
            return commentsVO;
        }).collect(Collectors.toList());
        Page<CommentsVO> resultPage = new PageDTO<>(commentsPage.getCurrent(), commentsPage.getSize(), commentsPage.getTotal());
        resultPage.setRecords(commentsVOList);
        return resultPage;
    }

    @Override
    public Boolean likeComment(CommentsLikeRequest commentslikeRequest, HttpServletRequest request) {
        // 检查评论 ID 是否为空
        ThrowUtils.throwIf(commentslikeRequest.getCommentId() == null, ErrorCode.PARAMS_ERROR, "评论 id 不能为空");
        // 获取用户信息
        User user = (User) request.getSession().getAttribute("user");

        // 创建更新包装器
        UpdateWrapper<Comments> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("commentId", commentslikeRequest.getCommentId());
        //判断评论是否getOne(updateWrapper);
        Comments comments = getOne(updateWrapper);
        if (comments == null) {
            return false;
        }

        if (commentslikeRequest.getLikeCount()!= null&& commentslikeRequest.getLikeCount() != 0) {
            // 处理点赞操作
            updateWrapper.setSql("likeCount = likeCount + "+commentslikeRequest.getLikeCount());
        }

        if(commentslikeRequest.getDislikeCount()!= null&& commentslikeRequest.getDislikeCount() != 0) {
            // 处理点踩操作
            updateWrapper.setSql("dislikeCount = dislikeCount + "+commentslikeRequest.getDislikeCount());
        }

        // 执行更新操作
        boolean result = update(updateWrapper);

        return result;
    }

    private List<CommentsVO> getChildrenComments(Long parentCommentId) {
        QueryWrapper<Comments> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parentCommentId", parentCommentId);
        // 按照创建时间倒序排列
        queryWrapper.orderByDesc("createTime");
        // 使用 CommentsService 的 list 方法查询子评论
        List<Comments> childrenComments = this.list(queryWrapper);
        if (childrenComments == null || childrenComments.isEmpty()) {
            return Collections.emptyList();
        }
        // 获取子评论的用户 ID 列表
        List<Long> childUserIds = childrenComments.stream()
                .map(Comments::getUserId)
                .collect(Collectors.toList());
        // 批量查询子评论的用户信息
        List<User> childUsers = userService.listByIds(childUserIds);
        List<CommentUserVO> childCommentUserVOs = childUsers.stream().map(user -> {
            CommentUserVO commentUserVO = new CommentUserVO();
            BeanUtils.copyProperties(user, commentUserVO);
            return commentUserVO;
        }).collect(Collectors.toList());
        Map<Long, CommentUserVO> childUserMap = childCommentUserVOs.stream()
                .collect(Collectors.toMap(CommentUserVO::getId, CommentUserVO -> CommentUserVO));
        return childrenComments.stream().map(comments -> {
            CommentsVO commentsVO = new CommentsVO();
            BeanUtils.copyProperties(comments, commentsVO);
            // 查找对应的子评论用户信息
            CommentUserVO commentUserVO = childUserMap.get(comments.getUserId());
            if (commentUserVO!= null) {
                commentsVO.setCommentUser(commentUserVO);
            }
            // 递归调用，查询子评论的子评论
            commentsVO.setChildren(getChildrenComments(comments.getCommentId()));
            return commentsVO;
        }).collect(Collectors.toList());
    }

    /**
     * 更新 ES 中图片的评论数
     */
    private void updateEsPictureCommentCount(Long pictureId, int delta) {
        try {
            // 先查询 ES 中是否存在该数据
            Optional<EsPicture> esOptional = esPictureDao.findById(pictureId);
            EsPicture esPicture;
            if (esOptional.isPresent()) {
                // 如果存在，只更新评论数
                esPicture = esOptional.get();
                long newCount = esPicture.getCommentCount() + delta;
                // 确保评论数不会小于0
                esPicture.setCommentCount(Math.max(0, newCount));
            } else {
                // 如果不存在，从 MySQL 获取完整数据
                Picture picture = pictureMapper.selectById(pictureId);
                if (picture == null) {
                    return;
                }
                esPicture = new EsPicture();
                BeanUtils.copyProperties(picture, esPicture);
            }
            esPictureDao.save(esPicture);
        } catch (Exception e) {
            log.error("Failed to update ES picture comment count, pictureId: {}, delta: {}", pictureId, delta, e);
        }
    }
}
