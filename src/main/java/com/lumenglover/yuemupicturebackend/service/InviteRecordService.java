package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.entity.InviteRecord;
import com.lumenglover.yuemupicturebackend.model.vo.InviteRecordVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserInviteRankVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

public interface InviteRecordService extends IService<InviteRecord> {

    /**
     * 获取邀请排行榜
     */
    List<UserInviteRankVO> getInviteLeaderboard(int limit);

    /**
     * 获取当前用户的邀请记录明细
     */
    Page<InviteRecordVO> listMyInviteRecords(long current, long size, HttpServletRequest request);
}
