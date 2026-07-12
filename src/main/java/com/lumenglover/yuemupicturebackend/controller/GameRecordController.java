package com.lumenglover.yuemupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.model.dto.gamerecord.GameRecordAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.gamerecord.GameRecordQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.GameRecord;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.GameRecordVO;
import com.lumenglover.yuemupicturebackend.service.GameRecordService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 统一游戏记录及排行榜控制器
 */
@RestController
@RequestMapping("/game/record")
public class GameRecordController {

    @Resource
    private GameRecordService gameRecordService;

    @Resource
    private UserService userService;

    /**
     * 保存当前游戏记录成绩
     *
     * @param request 记录添加参数
     * @param httpServletRequest http请求句柄
     * @return 基础响应体包含实体记录
     */
    @PostMapping("/save")
    @RateLimiter(key = "game_record_save", time = 3600, count = 100, message = "上传成绩过于频繁，请休息一下再试哦")
    public BaseResponse<GameRecord> saveGameRecord(@RequestBody GameRecordAddRequest request,
                                                   HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        GameRecord record = gameRecordService.saveGameRecord(request, loginUser);
        return ResultUtils.success(record);
    }

    /**
     * 分页查询当前登录用户的历史记录
     *
     * @param request 查询请求过滤参数
     * @param httpServletRequest http请求句柄
     * @return 分页 VO
     */
    @PostMapping("/my/history")
    @RateLimiter(key = "game_record_history", time = 60, count = 60, message = "查询过于频繁，请稍后再试")
    public BaseResponse<Page<GameRecordVO>> getMyHistoryRecords(@RequestBody GameRecordQueryRequest request,
                                                                HttpServletRequest httpServletRequest) {
        User loginUser = userService.getLoginUser(httpServletRequest);
        request.setUserId(loginUser.getId());
        Page<GameRecordVO> records = gameRecordService.listGameRecordVOByPage(request);
        return ResultUtils.success(records);
    }

    /**
     * 分页查询全局游戏记录 (管理员或系统日志检索使用)
     *
     * @param request 查询请求过滤参数
     * @return 分页 VO
     */
    @PostMapping("/list")
    @RateLimiter(key = "game_record_list", time = 60, count = 60, message = "查询过于频繁，请稍后再试")
    public BaseResponse<Page<GameRecordVO>> listGameRecordsByPage(@RequestBody GameRecordQueryRequest request) {
        Page<GameRecordVO> records = gameRecordService.listGameRecordVOByPage(request);
        return ResultUtils.success(records);
    }

    /**
     * 分页查询指定游戏类型和等级的排行榜
     *
     * @param request 查询请求过滤参数
     * @return 排行榜分页 VO
     */
    @PostMapping("/ranking")
    @RateLimiter(key = "game_record_ranking", time = 60, count = 60, message = "排行榜加载过于频繁，请稍后再试")
    public BaseResponse<Page<GameRecordVO>> getRankingList(@RequestBody GameRecordQueryRequest request) {
        Page<GameRecordVO> rankingList = gameRecordService.getGameRankingVOPage(request);
        return ResultUtils.success(rankingList);
    }
}
