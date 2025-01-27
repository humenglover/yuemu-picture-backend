package com.lumenglover.yuemupicturebackend.service;


import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.user.UserModifyPassWord;
import com.lumenglover.yuemupicturebackend.model.dto.user.UserQueryRequest;
import com.lumenglover.yuemupicturebackend.model.dto.user.UserUpdateRequest;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.LoginUserVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
* @author 鹿梦
* @description 针对表【user(用户)】的数据库操作Service
* @createDate 2024-12-10 10:39:52
*/
public interface UserService extends IService<User> {

    /**
     * 验证用户输入的验证码是否正确
     *
     * @param userInputCaptcha 用户输入的验证码
     * @param serververifycode 服务器端存储的加密后的验证码
     * @return 如果验证成功返回true，否则返回false
     */
    boolean validateCaptcha(String userInputCaptcha, String serververifycode);
    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获得脱敏后的登录用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 判断是否是登录态
     */
    User isLogin(HttpServletRequest request);

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获得脱敏后的用户信息列表
     *
     * @param userList
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取查询条件
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    boolean changePassword(UserModifyPassWord userModifyPassWord, HttpServletRequest request);

    boolean isAdmin(User loginUser);

    String updateUserAvatar(MultipartFile multipartFile, Long id, HttpServletRequest request);

    Map<String, String> getCaptcha();

    /**
     * 添加用户签到记录
     * @param userId 用户 id
     * @return 当前用户是否已签到成功
     */
    boolean addUserSignIn(long userId);

    /**
     * 获取用户某个年份的签到记录
     *
     * @param userId 用户 id
     * @param year   年份（为空表示当前年份）
     * @return 签到记录映射
     */
    List<Integer> getUserSignInRecord(long userId, Integer year);
}
