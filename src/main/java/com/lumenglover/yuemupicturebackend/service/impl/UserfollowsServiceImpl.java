package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.mapper.UserMapper;
import com.lumenglover.yuemupicturebackend.mapper.UserfollowsMapper;
import com.lumenglover.yuemupicturebackend.model.dto.userfollows.UserFollowsAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.userfollows.UserFollowsIsFollowsRequest;
import com.lumenglover.yuemupicturebackend.model.dto.userfollows.UserfollowsQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.entity.Userfollows;
import com.lumenglover.yuemupicturebackend.model.vo.FollowersAndFansVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.UserService;
import com.lumenglover.yuemupicturebackend.service.UserfollowsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 鹿梦
 * @description 针对表【userfollows】的数据库操作Service实现
 * @createDate 2025-01-14 20:49:17
 */
@Service
public class UserfollowsServiceImpl extends ServiceImpl<UserfollowsMapper, Userfollows>
        implements UserfollowsService {
    @Resource
    private UserMapper userMapper;

    @Resource
    private UserService userService;

    @Override
    @Transactional
    public Boolean addUserFollows(UserFollowsAddRequest userFollowsAddRequest) {
        Long followerId = userFollowsAddRequest.getFollowerId();
        Long followingId = userFollowsAddRequest.getFollowingId();
        Integer followStatus = userFollowsAddRequest.getFollowStatus();


        if (followerId == null || followingId == null || followStatus == null) {
            return false;
        }

        // 判断 followingId 是否存在
        ThrowUtils.throwIf(userMapper.selectCount(new QueryWrapper<User>().eq("id", followingId)) == 0, ErrorCode.OPERATION_ERROR, "followingId 不存在");

        Userfollows userfollows = new Userfollows();
        userfollows.setFollowerId(followerId);
        userfollows.setFollowingId(followingId);
        userfollows.setFollowStatus(followStatus);
        userfollows.setIsMutual(0);
        userfollows.setLastInteractionTime(new Date());

        // 创建 QueryWrapper 用于指定更新条件
        QueryWrapper<Userfollows> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followerId", followerId);
        queryWrapper.eq("followingId", followingId);

        // 判断是否存在
        if (this.getOne(queryWrapper)!= null) {
            // 如果存在，则更新 followStatus
            return this.update(userfollows, queryWrapper);
        }

        // 如果不存在，则插入新记录
        boolean result = this.save(userfollows);

        // 检查是否为双向关注
        if (checkMutualFollow(followerId, followingId)) {
            // 当是双向关注时，更新双方的 isMutual 字段和 lastInteractionTime 字段
            updateMutualFollow(followerId, followingId);
        }

        return result;
    }

    @Override
    public Page<UserVO> getFollowOrFanList(UserfollowsQueryRequest userfollowsQueryRequest) {
        ThrowUtils.throwIf(userfollowsQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userfollowsQueryRequest.getCurrent();
        long pageSize = userfollowsQueryRequest.getPageSize();
        Long followerId = userfollowsQueryRequest.getFollowerId();
        Long followingId = userfollowsQueryRequest.getFollowingId();
        int searchType = userfollowsQueryRequest.getSearchType();

        // 对current和pageSize进行非空和合理性判断
        if (current <= 0) {
            current = 1;
        }
        if (pageSize <= 0) {
            pageSize = 10;
        }

        QueryWrapper<Userfollows> queryWrapper = getUserfollowsQueryWrapper(userfollowsQueryRequest);
        IPage<Userfollows> userfollowsPage = this.page(new Page<>(current, pageSize), queryWrapper);

        List<Userfollows> userfollowsList = userfollowsPage.getRecords();

        if (userfollowsList == null || userfollowsList.isEmpty()) {
            return new Page<>();
        }

        List<Long> targetIdList;
        if (followerId!= null && searchType == 0) {
            targetIdList = userfollowsList.stream()
                    .map(Userfollows::getFollowingId)
                    .collect(Collectors.toList());
        } else if (followingId!= null && searchType == 1) {
            targetIdList = userfollowsList.stream()
                    .map(Userfollows::getFollowerId)
                    .collect(Collectors.toList());
        } else {
            return new Page<>();
        }

        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.in("id", targetIdList);

        List<User> userList = userMapper.selectList(userQueryWrapper);

        if (userList == null || userList.isEmpty()) {
            return new Page<>();
        }

        List<UserVO> userVOList = userService.getUserVOList(userList);

        // 将分页信息设置到 Page 对象中
        Page<UserVO> page = new Page<>(current, pageSize);
        page.setRecords(userVOList);
        page.setTotal(userfollowsPage.getTotal());

        return page;
    }

    private QueryWrapper<Userfollows> getUserfollowsQueryWrapper(UserfollowsQueryRequest userfollowsQueryRequest) {
        QueryWrapper<Userfollows> queryWrapper = new QueryWrapper<>();
        Long followerId = userfollowsQueryRequest.getFollowerId();
        Long followingId = userfollowsQueryRequest.getFollowingId();

        if (followerId!= null) {
            queryWrapper.eq("followerId", followerId);
        }
        if (followingId!= null) {
            queryWrapper.eq("followingId", followingId);
        }
        queryWrapper.eq("followStatus", 1);

        queryWrapper.orderByDesc("lastInteractionTime");
        return queryWrapper;
    }

    @Override
    public Boolean findIsFollow(UserFollowsIsFollowsRequest userFollowsIsFollowsRequest) {
        long followerId = userFollowsIsFollowsRequest.getFollowerId();
        long followingId = userFollowsIsFollowsRequest.getFollowingId();
        ThrowUtils.throwIf(followerId <= 0 || followingId <= 0, ErrorCode.PARAMS_ERROR);
        QueryWrapper<Userfollows> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followerId", followerId);
        queryWrapper.eq("followingId", followingId);
        queryWrapper.eq("followStatus", 1);
        return this.getOne(queryWrapper) != null;
    }

    @Override
    public List<Long> getFollowList(Long id) {
        if (id== null) {
            return null;
        }
        QueryWrapper<Userfollows> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followerId", id);
        queryWrapper.eq("followStatus", 1);
        List<Userfollows> userfollowsList = this.list(queryWrapper);
        return userfollowsList.stream().map(Userfollows::getFollowingId).collect(Collectors.toList());
    }

    @Override
    public FollowersAndFansVO getFollowAndFansCount(Long id) {
        QueryWrapper<Userfollows> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followerId", id);
        queryWrapper.eq("followStatus", 1);
        long followCount = this.count(queryWrapper);
        queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followingId", id);
        queryWrapper.eq("followStatus", 1);
        long fansCount = this.count(queryWrapper);
        FollowersAndFansVO followersAndFansVO = new FollowersAndFansVO();
        followersAndFansVO.setFollowCount(followCount);
        followersAndFansVO.setFansCount(fansCount);
        return followersAndFansVO;
    }

    public boolean checkMutualFollow(Long userId1, Long userId2) {
        // 检查用户 1 关注用户 2 的记录是否存在
        QueryWrapper<Userfollows> queryWrapper1 = new QueryWrapper<>();
        queryWrapper1.eq("followerId", userId1);
        queryWrapper1.eq("followingId", userId2);
        Userfollows follow1 = this.getOne(queryWrapper1);

        // 检查用户 2 关注用户 1 的记录是否存在
        QueryWrapper<Userfollows> queryWrapper2 = new QueryWrapper<>();
        queryWrapper2.eq("followerId", userId2);
        queryWrapper2.eq("followingId", userId1);
        Userfollows follow2 = this.getOne(queryWrapper2);

        // 当用户 1 关注用户 2 且用户 2 关注用户 1 时，认为是双向关注
        return follow1!= null && follow2!= null;
    }

    private void updateMutualFollow(Long userId1, Long userId2) {
        Date now = new Date();

        // 更新用户 1 关注用户 2 的记录
        QueryWrapper<Userfollows> queryWrapper1 = new QueryWrapper<>();
        queryWrapper1.eq("followerId", userId1);
        queryWrapper1.eq("followingId", userId2);
        Userfollows updateUserfollows1 = new Userfollows();
        updateUserfollows1.setIsMutual(1);
        updateUserfollows1.setLastInteractionTime(now);
        this.update(updateUserfollows1, queryWrapper1);

        // 更新用户 2 关注用户 1 的记录
        QueryWrapper<Userfollows> queryWrapper2 = new QueryWrapper<>();
        queryWrapper2.eq("followerId", userId2);
        queryWrapper2.eq("followingId", userId1);
        Userfollows updateUserfollows2 = new Userfollows();
        updateUserfollows2.setIsMutual(1);
        updateUserfollows2.setLastInteractionTime(now);
        this.update(updateUserfollows2, queryWrapper2);
    }
}
