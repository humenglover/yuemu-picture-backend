package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.model.dto.es.EsPictureDao;
import com.lumenglover.yuemupicturebackend.mapper.PictureMapper;
import com.lumenglover.yuemupicturebackend.mapper.PicturelikeMapper;
import com.lumenglover.yuemupicturebackend.model.dto.picturelike.PictureLikeRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.Picturelike;
import com.lumenglover.yuemupicturebackend.model.entity.es.EsPicture;
import com.lumenglover.yuemupicturebackend.service.PicturelikeService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author 鹿梦
 * @description 针对表【picturelike(点赞表)】的数据库操作Service实现
 * @createDate 2024-12-29 11:25:37
 */
@Service
@Slf4j
public class PicturelikeServiceImpl extends ServiceImpl<PicturelikeMapper, Picturelike>
        implements PicturelikeService {
    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private EsPictureDao esPictureDao;

    @Override
    @Async("asyncExecutor")
    public CompletableFuture<Boolean> UserLike(PictureLikeRequest pictureLikeRequest, Long userId) {
        try {
            Long pictureId = pictureLikeRequest.getPictureId();
            Integer isLiked = pictureLikeRequest.getIsLiked();

            // 参数校验
            if (pictureId == null || userId == null || isLiked == null) {
                log.error("Invalid parameters: pictureId={}, userId={}, isLiked={}", pictureId, userId, isLiked);
                return CompletableFuture.completedFuture(false);
            }

            // 检查图片是否存在
            Picture picture = pictureMapper.selectById(pictureId);
            if (picture == null) {
                log.error("Picture not found: pictureId={}", pictureId);
                return CompletableFuture.completedFuture(false);
            }

            // 查询当前点赞状态
            QueryWrapper<Picturelike> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId)
                    .eq("pictureId", pictureId);
            Picturelike oldPicturelike = this.getOne(queryWrapper);

            UpdateWrapper<Picture> pictureupdateWrapper = new UpdateWrapper<>();
            pictureupdateWrapper.eq("id", pictureId);

            // 处理首次点赞的情况
            if (oldPicturelike == null) {
                if (isLiked == 1) {
                    Picturelike picturelike = new Picturelike();
                    picturelike.setPictureId(pictureId);
                    picturelike.setUserId(userId);
                    picturelike.setIsLiked(1);
                    picturelike.setLastLikeTime(new Date());
                    this.save(picturelike);

                    pictureupdateWrapper.setSql("likeCount = likeCount + 1");
                    pictureMapper.update(null, pictureupdateWrapper);

                    updateEsPictureLikeCount(pictureId, 1);
                }
                // 首次取消点赞，静默返回成功
                return CompletableFuture.completedFuture(true);
            } else {
                // 处理已存在点赞记录的情况
                int currentLikeStatus = oldPicturelike.getIsLiked();

                // 如果状态相同，静默返回成功
                if (isLiked == currentLikeStatus) {
                    return CompletableFuture.completedFuture(true);
                }

                // 更新点赞状态
                oldPicturelike.setIsLiked(isLiked);
                oldPicturelike.setLastLikeTime(new Date());
                this.updateById(oldPicturelike);

                // 更新 MySQL 和 ES
                if (isLiked == 1) {
                    pictureupdateWrapper.setSql("likeCount = likeCount + 1");
                    updateEsPictureLikeCount(pictureId, 1);
                } else {
                    pictureupdateWrapper.setSql("likeCount = likeCount - 1");
                    updateEsPictureLikeCount(pictureId, -1);
                }
                pictureMapper.update(null, pictureupdateWrapper);
            }

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Error in UserLike: ", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    @Async("asyncExecutor")
    public CompletableFuture<Boolean> UserShare(String pictureId, Long userId) {
        try {
            UpdateWrapper<Picture> pictureupdateWrapper = new UpdateWrapper<>();
            pictureupdateWrapper.eq("id", pictureId);
            pictureupdateWrapper.setSql("shareCount = shareCount + 1");
            pictureMapper.update(null, pictureupdateWrapper);

            updateEsPictureShareCount(Long.parseLong(pictureId), 1);

            return CompletableFuture.completedFuture(true);
        } catch (Exception e) {
            log.error("Error in UserShare: ", e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * 更新 ES 中图片的点赞数
     */
    private void updateEsPictureLikeCount(Long pictureId, int delta) {
        try {
            // 先查询 ES 中是否存在该数据
            Optional<EsPicture> esOptional = esPictureDao.findById(pictureId);
            EsPicture esPicture;
            if (esOptional.isPresent()) {
                // 如果存在，只更新点赞数
                esPicture = esOptional.get();
                esPicture.setLikeCount(esPicture.getLikeCount() + delta);
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
            log.error("Failed to update ES picture like count, pictureId: {}", pictureId, e);
        }
    }

    /**
     * 更新 ES 中图片的分享数
     */
    private void updateEsPictureShareCount(Long pictureId, int delta) {
        try {
            // 先查询 ES 中是否存在该数据
            Optional<EsPicture> esOptional = esPictureDao.findById(pictureId);
            EsPicture esPicture;
            if (esOptional.isPresent()) {
                // 如果存在，只更新分享数
                esPicture = esOptional.get();
                esPicture.setShareCount(esPicture.getShareCount() + delta);
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
            log.error("Failed to update ES picture share count, pictureId: {}", pictureId, e);
        }
    }
}

