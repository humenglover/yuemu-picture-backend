package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.picturelike.PictureLikeRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Picturelike;

import java.util.concurrent.CompletableFuture;

public interface PicturelikeService extends IService<Picturelike> {
    CompletableFuture<Boolean> UserLike(PictureLikeRequest pictureLikeRequest, Long userId);
    CompletableFuture<Boolean> UserShare(String pictureId, Long userId);
}
