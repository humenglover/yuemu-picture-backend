package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.mapper.GameRecordMapper;
import com.lumenglover.yuemupicturebackend.model.dto.gamerecord.GameRecordAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.gamerecord.GameRecordQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.GameRecord;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.enums.GameTypeEnum;
import com.lumenglover.yuemupicturebackend.model.vo.GameRecordVO;
import com.lumenglover.yuemupicturebackend.service.GameRecordService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 统一游戏记录服务实现类
 */
@Service
public class GameRecordServiceImpl extends ServiceImpl<GameRecordMapper, GameRecord>
        implements GameRecordService {

    @Resource
    private UserService userService;

    @Override
    public GameRecord saveGameRecord(GameRecordAddRequest request, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        String gameType = request.getGameType();
        Integer score = request.getScore();

        ThrowUtils.throwIf(StrUtil.isBlank(gameType), ErrorCode.PARAMS_ERROR, "游戏类型不能为空");
        ThrowUtils.throwIf(score == null || score < 0, ErrorCode.PARAMS_ERROR, "游戏分数不合法");

        // 校验游戏类型枚举是否合法
        GameTypeEnum gameTypeEnum = GameTypeEnum.getEnumByValue(gameType);
        ThrowUtils.throwIf(gameTypeEnum == null, ErrorCode.PARAMS_ERROR, "无效的游戏类型");

        // 构建游戏记录
        GameRecord gameRecord = new GameRecord();
        gameRecord.setUserId(loginUser.getId());
        gameRecord.setGameType(gameType);
        gameRecord.setGameName(gameTypeEnum.getText());
        gameRecord.setLevel(request.getLevel());
        gameRecord.setScore(score);

        boolean result = this.save(gameRecord);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "保存游戏记录失败");

        return gameRecord;
    }

    @Override
    public Page<GameRecordVO> listGameRecordVOByPage(GameRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        int current = request.getCurrent();
        int pageSize = request.getPageSize();
        Long userId = request.getUserId();
        String gameType = request.getGameType();
        String level = request.getLevel();

        Page<GameRecord> page = new Page<>(current, pageSize);
        QueryWrapper<GameRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("isDelete", 0);

        if (userId != null) {
            queryWrapper.eq("userId", userId);
        }
        if (StrUtil.isNotBlank(gameType)) {
            queryWrapper.eq("gameType", gameType);
        }
        if (StrUtil.isNotBlank(level)) {
            queryWrapper.eq("level", level);
        }

        // 个人历史按达成时间降序
        queryWrapper.orderByDesc("createTime");

        Page<GameRecord> gameRecordPage = this.page(page, queryWrapper);
        return getGameRecordVOPage(gameRecordPage);
    }

    @Override
    public Page<GameRecordVO> getGameRankingVOPage(GameRecordQueryRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);

        String gameType = request.getGameType();
        String level = request.getLevel();
        int current = request.getCurrent();
        int pageSize = request.getPageSize();

        ThrowUtils.throwIf(StrUtil.isBlank(gameType), ErrorCode.PARAMS_ERROR, "游戏类型不能为空");

        Page<GameRecord> page = new Page<>(current, pageSize);

        // 调用自定义 XML Mapper 子查询去重语句，实现每个用户在特定级别下仅有一项最高纪录
        Page<GameRecord> gameRecordPage = this.baseMapper.selectLeaderboardPage(page, gameType, level);
        return getGameRecordVOPage(gameRecordPage);
    }

    /**
     * Entity 分页映射为 VO 分页并关联查询用户信息 (IN 方式批量关联，防御 N+1 查询隐患)
     */
    private Page<GameRecordVO> getGameRecordVOPage(Page<GameRecord> gameRecordPage) {
        List<GameRecord> records = gameRecordPage.getRecords();
        long current = gameRecordPage.getCurrent();
        long size = gameRecordPage.getSize();
        long total = gameRecordPage.getTotal();

        Page<GameRecordVO> voPage = new Page<>(current, size, total);
        if (CollUtil.isEmpty(records)) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }

        // 过滤关联的用户 ID
        List<Long> userIds = records.stream()
                .map(GameRecord::getUserId)
                .distinct()
                .collect(Collectors.toList());

        // 批量拉取用户信息
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 绑定字段
        List<GameRecordVO> voList = records.stream().map(record -> {
            GameRecordVO vo = new GameRecordVO();
            vo.setId(record.getId());
            vo.setUserId(record.getUserId());
            vo.setGameType(record.getGameType());
            vo.setGameName(record.getGameName());
            vo.setLevel(record.getLevel());
            vo.setScore(record.getScore());
            vo.setCreateTime(record.getCreateTime());

            User user = userMap.get(record.getUserId());
            if (user != null) {
                vo.setUserName(user.getUserName());
                vo.setUserAvatar(user.getUserAvatar());
            }
            return vo;
        }).collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }
}
