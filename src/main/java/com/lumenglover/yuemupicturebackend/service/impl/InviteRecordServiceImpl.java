package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.mapper.InviteRecordMapper;
import com.lumenglover.yuemupicturebackend.model.entity.InviteRecord;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.InviteRecordVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserInviteRankVO;
import com.lumenglover.yuemupicturebackend.service.InviteRecordService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class InviteRecordServiceImpl extends ServiceImpl<InviteRecordMapper, InviteRecord> implements InviteRecordService {

    @Resource
    private UserService userService;

    @Override
    public List<UserInviteRankVO> getInviteLeaderboard(int limit) {
        QueryWrapper<InviteRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("inviterId", "COUNT(id) as count");
        queryWrapper.eq("status", 1);
        queryWrapper.eq("isDelete", 0);
        queryWrapper.groupBy("inviterId");
        queryWrapper.orderByDesc("count");
        queryWrapper.last("LIMIT " + limit);

        List<Map<String, Object>> mapList = this.listMaps(queryWrapper);
        List<UserInviteRankVO> rankList = new ArrayList<>();

        for (Map<String, Object> map : mapList) {
            Object inviterIdObj = map.get("inviterId");
            Object countObj = map.get("count");

            if (inviterIdObj != null && countObj != null) {
                Long inviterId = ((Number) inviterIdObj).longValue();
                int count = ((Number) countObj).intValue();

                User user = userService.getById(inviterId);
                if (user != null) {
                    UserInviteRankVO vo = new UserInviteRankVO();
                    vo.setUserId(user.getId());
                    vo.setUserName(user.getUserName());
                    vo.setUserAvatar(user.getUserAvatar());
                    vo.setInviteCount(count);
                    rankList.add(vo);
                }
            }
        }
        return rankList;
    }

    @Override
    public Page<InviteRecordVO> listMyInviteRecords(long current, long size, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        QueryWrapper<InviteRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("inviterId", loginUser.getId());
        queryWrapper.eq("isDelete", 0);
        queryWrapper.orderByDesc("createTime");

        Page<InviteRecord> page = this.page(new Page<>(current, size), queryWrapper);
        Page<InviteRecordVO> voPage = new Page<>(current, size, page.getTotal());

        List<InviteRecordVO> voList = page.getRecords().stream().map(record -> {
            InviteRecordVO vo = new InviteRecordVO();
            vo.setId(record.getId());
            vo.setInviteeId(record.getInviteeId());
            vo.setStatus(record.getStatus());
            vo.setCreateTime(record.getCreateTime());
            vo.setConfirmTime(record.getConfirmTime());

            User invitee = userService.getById(record.getInviteeId());
            if (invitee != null) {
                vo.setInviteeName(invitee.getUserName());
                vo.setInviteeAvatar(invitee.getUserAvatar());
            } else {
                vo.setInviteeName("已注销用户");
            }
            return vo;
        }).collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }
}
