package com.lumenglover.yuemupicturebackend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.model.vo.InviteRecordVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserInviteRankVO;
import com.lumenglover.yuemupicturebackend.service.InviteRecordService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 邀请记录接口
 */
@RestController
@RequestMapping("/invite")
public class InviteController {

    @Resource
    private InviteRecordService inviteRecordService;

    /**
     * 获取邀请排行榜
     */
    @GetMapping("/leaderboard")
    public BaseResponse<List<UserInviteRankVO>> getLeaderboard(@RequestParam(defaultValue = "10") int limit) {
        List<UserInviteRankVO> rankList = inviteRecordService.getInviteLeaderboard(limit);
        return ResultUtils.success(rankList);
    }

    /**
     * 获取我的邀请明细
     */
    @GetMapping("/my/records")
    public BaseResponse<Page<InviteRecordVO>> listMyInviteRecords(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            HttpServletRequest request) {
        Page<InviteRecordVO> page = inviteRecordService.listMyInviteRecords(current, size, request);
        return ResultUtils.success(page);
    }
}
