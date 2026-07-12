package com.lumenglover.yuemupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumenglover.yuemupicturebackend.model.entity.AuthorRanking;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 作者榜单 Mapper
 */
public interface AuthorRankingMapper extends BaseMapper<AuthorRanking> {

    /**
     * 统计图片作者数据
     */
    List<Map<String, Object>> calculatePictureAuthorStats(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );

    /**
     * 统计帖子作者数据
     */
    List<Map<String, Object>> calculatePostAuthorStats(
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime
    );

    /**
     * 批量插入或更新榜单数据
     */
    int batchInsertOrUpdate(@Param("list") List<AuthorRanking> list);
}
