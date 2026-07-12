package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.loginrecord.UserLoginRecordQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.entity.UserLoginRecord;
import com.lumenglover.yuemupicturebackend.model.vo.UserLoginRecordVO;

import javax.servlet.http.HttpServletRequest;

/**
 * 用户登录记录服务
 */
public interface UserLoginRecordService extends IService<UserLoginRecord> {

    /**
     * 记录用户登录信息
     *
     * @param user 用户信息
     * @param loginMethod 登录方式
     * @param request HTTP请求
     * @return 登录记录ID
     */
    Long recordLogin(User user, String loginMethod, HttpServletRequest request);

    /**
     * 记录登录失败
     *
     * @param userId 用户ID（可能为null）
     * @param loginMethod 登录方式
     * @param request HTTP请求
     * @param failReason 失败原因
     */
    void recordLoginFailure(Long userId, String loginMethod, HttpServletRequest request, String failReason);

    /**
     * 获取查询条件
     *
     * @param queryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper<UserLoginRecord> getQueryWrapper(UserLoginRecordQueryRequest queryRequest);

    /**
     * 获取登录记录视图对象
     *
     * @param loginRecord 登录记录
     * @param request HTTP请求
     * @return 登录记录视图对象
     */
    UserLoginRecordVO getLoginRecordVO(UserLoginRecord loginRecord, HttpServletRequest request);

    /**
     * 分页获取登录记录视图对象
     *
     * @param loginRecordPage 登录记录分页
     * @param request HTTP请求
     * @return 登录记录视图对象分页
     */
    Page<UserLoginRecordVO> getLoginRecordVOPage(Page<UserLoginRecord> loginRecordPage, HttpServletRequest request);

    /**
     * 检测登录风险
     *
     * @param userId 用户ID
     * @param loginIp 登录IP
     * @param deviceInfo 设备信息
     * @return 风险等级 0-正常 1-可疑 2-高危
     */
    int detectLoginRisk(Long userId, String loginIp, String deviceInfo);

    /**
     * 发送登录通知
     *
     * @param loginRecord 登录记录
     */
    void sendLoginNotification(UserLoginRecord loginRecord);

    /**
     * 删除登录记录并踢出对应的会话
     *
     * @param id 登录记录ID
     * @param currentUserId 当前用户ID
     * @return 是否删除成功
     */
    boolean deleteLoginRecordAndKickout(Long id, Long currentUserId);

    /**
     * 批量删除登录记录并踢出对应的会话
     *
     * @param ids 登录记录ID列表
     * @param currentUserId 当前用户ID
     * @return 是否删除成功
     */
    boolean batchDeleteLoginRecordAndKickout(Long[] ids, Long currentUserId);
}
