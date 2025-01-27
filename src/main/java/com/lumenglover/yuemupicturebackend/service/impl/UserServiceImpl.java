package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.captcha.generator.RandomGenerator;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.constant.CommonValue;
import com.lumenglover.yuemupicturebackend.constant.RedisConstant;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import com.lumenglover.yuemupicturebackend.model.dto.es.EsUserDao;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.manager.FileManager;
import com.lumenglover.yuemupicturebackend.manager.auth.StpKit;
import com.lumenglover.yuemupicturebackend.mapper.UserMapper;
import com.lumenglover.yuemupicturebackend.mapper.UserSignInRecordMapper;
import com.lumenglover.yuemupicturebackend.model.dto.file.UploadPictureResult;
import com.lumenglover.yuemupicturebackend.model.dto.user.UserModifyPassWord;
import com.lumenglover.yuemupicturebackend.model.dto.user.UserQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.entity.UserSignInRecord;
import com.lumenglover.yuemupicturebackend.model.entity.es.EsUser;
import com.lumenglover.yuemupicturebackend.model.enums.UserRoleEnum;
import com.lumenglover.yuemupicturebackend.model.vo.LoginUserVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author 鹿梦
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2024-12-10 10:39:52
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private UserMapper userMapper;
    @Resource
    private FileManager fileManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserSignInRecordMapper userSignInRecordMapper;

    @Resource
    private EsUserDao esUserDao;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 用户注册成功后的ID
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数合法性
        validateUserInputParams(userAccount, userPassword, checkPassword, true);

        // 2. 检查用户账号是否重复
        long count=userMapper.selectByAccount(userAccount);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该账号已被注册，请更换账号");
        }

        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);

        // 4. 构建并插入用户数据
        User user = buildUserForRegistration(userAccount, encryptPassword);
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库操作出现问题，请稍后再试");
        }
        return user.getId();
    }

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      HttpServletRequest对象
     * @return 登录后的用户信息视图对象（LoginUserVO）
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数合法性
        validateUserInputParams(userAccount, userPassword, null, false);

        // 2. 加密用户输入的密码
        String encryptPassword = getEncryptPassword(userPassword);

        // 3. 查询数据库验证用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户名或密码错误，请核对后重新登录");
        }

        // 4. 保存用户登录态并返回脱敏后的用户信息
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);
        return getLoginUserVO(user);
    }

    /**
     * 获取加密后的密码
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 加盐，混淆密码
        return SecureUtil.md5(CommonValue.DEFAULT_SALT + userPassword);
    }

    // 以下是其他未修改的方法，省略了详细代码，可根据实际情况继续完善或优化

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断是否已经登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库中查询（追求性能的话可以注释，直接返回上述结果）
        Long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取脱敏类的用户信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public User isLogin(HttpServletRequest request) {
        // 判断是否已经登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            return null;
        }
        // 从数据库中查询（追求性能的话可以注释，直接返回上述结果）
        Long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            return null;
        }
        return currentUser;
    }

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 判断是否已经登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        // 移除 Sa-Token 登录态
        StpKit.SPACE.logout();
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public boolean changePassword(UserModifyPassWord userModifyPassWord, HttpServletRequest request) {
        if(StrUtil.hasBlank(userModifyPassWord.getOldPassword(), userModifyPassWord.getNewPassword(), userModifyPassWord.getCheckPassword())){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if(!userModifyPassWord.getNewPassword().equals(userModifyPassWord.getCheckPassword())){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        if(userModifyPassWord.getNewPassword().length() < 8){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "新密码长度不能小于8位");
        }
        //查询是否有这个用户
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", userModifyPassWord.getId());
        String encryptPassword = getEncryptPassword(userModifyPassWord.getOldPassword());
        queryWrapper.eq("userPassword", encryptPassword);
        User user = userMapper.selectOne(queryWrapper);
        if(user == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "原密码错误");
        }

        user.setUserPassword(getEncryptPassword(userModifyPassWord.getNewPassword()));
        // 更新MySQL
        boolean result = userMapper.updateById(user) > 0;
        if (result) {
            // 更新ES
            EsUser esUser = new EsUser();
            BeanUtil.copyProperties(user, esUser);
            esUserDao.save(esUser);
        }
        return result;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    @Override
    public String updateUserAvatar(MultipartFile multipartFile, Long id, HttpServletRequest request) {
        //判断用户是否存在
        User user = userMapper.selectById(id);
        if(user == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        //判断用户是否登录
        User loginUser = getLoginUser(request);
        if(loginUser == null || !loginUser.getId().equals(id)){
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }
        //判断文件是否为空
        if(multipartFile == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }
        //判断文件类型
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录
        String uploadPathPrefix = String.format("public/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);
        //更新用户头像
        user.setUserAvatar(uploadPictureResult.getUrl());
        // 更新MySQL
        boolean result = userMapper.updateById(user) > 0;
        if (result) {
            // 更新ES
            EsUser esUser = new EsUser();
            BeanUtil.copyProperties(user, esUser);
            esUserDao.save(esUser);
        }
        return uploadPictureResult.getUrl();
    }

    @Override
    public Map<String, String> getCaptcha() {
        // 仅包含数字的字符集
        String characters = "0123456789";
        // 生成 4 位数字验证码
        RandomGenerator randomGenerator = new RandomGenerator(characters, 4);
        // 定义图片的显示大小，并创建验证码对象
        ShearCaptcha shearCaptcha = CaptchaUtil.createShearCaptcha(320, 100, 4, 4);
        shearCaptcha.setGenerator(randomGenerator);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        shearCaptcha.write(outputStream);
        byte[] captchaBytes = outputStream.toByteArray();
        String base64Captcha = Base64.getEncoder().encodeToString(captchaBytes);
        String captchaCode = shearCaptcha.getCode();

        // 使用 Hutool 的 MD5 加密
        String encryptedCaptcha = DigestUtil.md5Hex(captchaCode);

        // 将加密后的验证码和 Base64 编码的图片存储到 Redis 中，设置过期时间为 5 分钟（300 秒）
        stringRedisTemplate.opsForValue().set("captcha:" + encryptedCaptcha, captchaCode, 300, TimeUnit.SECONDS);

        Map<String, String> data = new HashMap<>();
        data.put("base64Captcha", base64Captcha);
        data.put("encryptedCaptcha", encryptedCaptcha);
        return data;
    }

    /**
     * 添加用户签到记录
     * @param userId 用户 id
     * @return 当前用户是否已签到成功
     */
    @Override
    public boolean addUserSignIn(long userId) {
        LocalDate date = LocalDate.now();
        int currentYear = date.getYear();
        String redisKey = RedisConstant.getUserSignInRedisKey(currentYear, userId);

        // 获取 Redis 的 BitMap
        RBitSet signInBitSet = redissonClient.getBitSet(redisKey);
        int dayOfYear = date.getDayOfYear();

        // 查询当天有没有签到
        if (!signInBitSet.get(dayOfYear)) {
            // 如果当前未签到，则设置Redis
            signInBitSet.set(dayOfYear, true);

            // 设置 Redis 键的过期时间到当年最后一天
            LocalDate endOfYear = LocalDate.of(currentYear, 12, 31);
            Duration timeUntilEndOfYear = Duration.between(
                    LocalDateTime.now(),
                    endOfYear.atTime(23, 59, 59)
            );
            redissonClient.getBucket(redisKey).expire(timeUntilEndOfYear);
        }

        return true;
    }

    /**
     * 获取用户某个年份的签到记录
     *
     * @param userId 用户 id
     * @param year   年份（为空表示当前年份）
     * @return 签到记录映射
     */
    @Override
    public List<Integer> getUserSignInRecord(long userId, Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }

        int currentYear = LocalDate.now().getYear();
        List<Integer> signInDays = new ArrayList<>();

        if (year != currentYear) {
            // 非当年数据直接从MySQL查询
            QueryWrapper<UserSignInRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId)
                    .eq("year", year);

            UserSignInRecord record = userSignInRecordMapper.selectOne(queryWrapper);
            if (record != null && record.getSignInData() != null) {
                byte[] signInData = record.getSignInData();
                // 解析bitmap数据
                for (int day = 1; day <= 366; day++) {
                    int byteIndex = (day - 1) / 8;
                    int bitIndex = (day - 1) % 8;
                    if ((signInData[byteIndex] & (1 << bitIndex)) != 0) {
                        signInDays.add(day);
                    }
                }
            }
            return signInDays;
        }

        // 当年数据从Redis获取
        String redisKey = RedisConstant.getUserSignInRedisKey(year, userId);
        RBitSet signInBitSet = redissonClient.getBitSet(redisKey);

        // 如果Redis中没有数据，从MySQL加载
        if (!signInBitSet.isExists()) {
            QueryWrapper<UserSignInRecord> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId)
                    .eq("year", year);

            UserSignInRecord record = userSignInRecordMapper.selectOne(queryWrapper);
            if (record != null && record.getSignInData() != null) {
                byte[] signInData = record.getSignInData();
                // 将MySQL中的bitmap数据加载到Redis
                for (int day = 1; day <= 366; day++) {
                    int byteIndex = (day - 1) / 8;
                    int bitIndex = (day - 1) % 8;
                    if ((signInData[byteIndex] & (1 << bitIndex)) != 0) {
                        signInBitSet.set(day, true);
                    }
                }

                // 设置过期时间到年底
                LocalDate endOfYear = LocalDate.of(year, 12, 31);
                Duration timeUntilEndOfYear = Duration.between(
                        LocalDateTime.now(),
                        endOfYear.atTime(23, 59, 59)
                );
                redissonClient.getBucket(redisKey).expire(timeUntilEndOfYear);
            }
        }

        // 从Redis的bitmap中获取签到记录
        BitSet bitSet = signInBitSet.asBitSet();
        int index = bitSet.nextSetBit(0);
        while (index >= 0) {
            signInDays.add(index);
            index = bitSet.nextSetBit(index + 1);
        }

        return signInDays;
    }

    @Override
    public boolean validateCaptcha(String userInputCaptcha, String serververifycode) {
        if (userInputCaptcha!= null && serververifycode!= null) {
            // 使用Hutool对用户输入的验证码进行MD5加密
            String encryptedVerifycode = DigestUtil.md5Hex(userInputCaptcha);
            if(encryptedVerifycode.equals(serververifycode)){
                return true;
            }
        }
        throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误");
    }

    /**
     * 校验用户相关输入参数的合法性
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码（注册时需要，登录时传null）
     * @param isRegister    是否为注册操作
     */
    private void validateUserInputParams(String userAccount, String userPassword, String checkPassword, boolean isRegister) {
        if (StrUtil.hasBlank(userAccount, userPassword) || (isRegister && StrUtil.hasBlank(checkPassword))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号长度不能小于4位");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码长度不能小于8位");
        }
        if (isRegister &&!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
    }

    /**
     * 根据注册信息构建User对象
     *
     * @param userAccount    用户账户
     * @param encryptPassword 加密后的密码
     * @return 构建好的User对象
     */
    private User buildUserForRegistration(String userAccount, String encryptPassword) {
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName(userAccount);
        user.setUserRole(UserRoleEnum.USER.getValue());
        return user;
    }

    @Override
    public boolean removeById(Serializable id) {
        // 从MySQL删除
        boolean result = super.removeById(id);
        if (result) {
            // 从ES删除
            esUserDao.deleteById((Long) id);
        }
        return result;
    }

    @Override
    public boolean removeByIds(Collection<?> idList) {
        // 从MySQL批量删除
        boolean result = super.removeByIds(idList);
        if (result) {
            // 从ES批量删除
            idList.forEach(id -> esUserDao.deleteById((Long) id));
        }
        return result;
    }

    @Override
    public boolean updateById(User entity) {
        // 更新MySQL
        boolean result = super.updateById(entity);
        if (result) {
            // 更新ES
            // 获取完整的用户信息
            User updatedUser = this.getById(entity.getId());
            // 转换为ES实体
            EsUser esUser = new EsUser();
            BeanUtil.copyProperties(updatedUser, esUser);
            esUserDao.save(esUser);
        }
        return result;
    }
}
