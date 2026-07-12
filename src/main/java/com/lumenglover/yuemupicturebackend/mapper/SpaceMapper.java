package com.lumenglover.yuemupicturebackend.mapper;
import com.lumenglover.yuemupicturebackend.model.entity.Space;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * @author 鹿梦
 * @description 针对表【space(空间)】的数据库操作Mapper
 * @createDate 2024-12-18 19:53:34
 * @Entity com.lumenglover.yuemupicturebackend.model.entity.Space
 */
import java.util.List;
import java.util.Date;

public interface SpaceMapper extends BaseMapper<Space> {

    /**
     * 查询已删除的空间（绕过逻辑删除）
     * @param updateTime 更新时间
     * @return 已删除的空间列表
     */
    List<Space> selectDeletedSpacesByUpdateTime(Date updateTime);

    /**
     * 查询所有已删除的空间（绕过逻辑删除）
     * @return 已删除的空间列表
     */
    List<Space> selectAllDeletedSpaces();

    /**
     * 原子增加空间已使用容量（并校验不超最大容量）
     * @param spaceId
     * @param fileSize
     * @return 影响行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE space SET usedStorage = usedStorage + #{fileSize}, updateTime = NOW() WHERE id = #{spaceId} AND (usedStorage + #{fileSize}) <= maxStorage")
    int addSpaceUsedStorage(@org.apache.ibatis.annotations.Param("spaceId") Long spaceId, @org.apache.ibatis.annotations.Param("fileSize") Long fileSize);

    /**
     * 原子减少空间已使用容量
     * @param spaceId
     * @param fileSize
     * @return 影响行数
     */
    @org.apache.ibatis.annotations.Update("UPDATE space SET usedStorage = usedStorage - #{fileSize}, updateTime = NOW() WHERE id = #{spaceId}")
    int deductSpaceUsedStorage(@org.apache.ibatis.annotations.Param("spaceId") Long spaceId, @org.apache.ibatis.annotations.Param("fileSize") Long fileSize);
}




