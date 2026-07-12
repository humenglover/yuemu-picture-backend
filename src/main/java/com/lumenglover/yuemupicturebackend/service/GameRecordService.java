package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.gamerecord.GameRecordAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.gamerecord.GameRecordQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.GameRecord;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.GameRecordVO;

/**
 * 统一游戏记录服务接口
 */
public interface GameRecordService extends IService<GameRecord> {

    /**
     * 保存游戏记录
     *
     * @param request 保存成绩请求
     * @param loginUser 登录用户
     * @return 游戏记录实体
     */
    GameRecord saveGameRecord(GameRecordAddRequest request, User loginUser);

    /**
     * 分页查询游戏记录列表 (用户历史记录)
     *
     * @param request 查询请求参数
     * @return 分页 VO
     */
    Page<GameRecordVO> listGameRecordVOByPage(GameRecordQueryRequest request);

    /**
     * 分页查询用户特定等级的排行榜
     *
     * @param request 排行榜查询请求参数
     * @return 分页排行榜 VO
     */
    Page<GameRecordVO> getGameRankingVOPage(GameRecordQueryRequest request);
}
