package com.lumenglover.yuemupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.model.entity.GameRecord;
import org.apache.ibatis.annotations.Param;

/**
 * 统一游戏记录 Mapper 接口
 */
public interface GameRecordMapper extends BaseMapper<GameRecord> {

    /**
     * 分页查询用户特定等级的排行榜 (每个用户仅保留最高分，实现完美排重)
     *
     * @param page 分页对象
     * @param gameType 游戏类型标识
     * @param level 关卡/级别标识
     * @return 游戏记录实体分页
     */
    Page<GameRecord> selectLeaderboardPage(
            Page<GameRecord> page,
            @Param("gameType") String gameType,
            @Param("level") String level
    );
}
