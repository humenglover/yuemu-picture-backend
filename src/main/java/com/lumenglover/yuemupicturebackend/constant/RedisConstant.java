package com.lumenglover.yuemupicturebackend.constant;

/**
 * Redis 常量
 */
public interface RedisConstant {

    /**
     * 用户签到记录的 Redis key 前缀
     */
    String USER_SIGN_IN_REDIS_KEY_PREFIX = "user:signins";

    /**
     * top100
     */
    String TOP_100_PIC_REDIS_KEY_PREFIX = "top100Picture:";

    /**
     * top100过期时间为1天
     */
    long TOP_100_PIC_REDIS_KEY_EXPIRE_TIME =  24 * 60 * 60;

    /**
     * 公共图库前置
     */
    String PUBLIC_PIC_REDIS_KEY_PREFIX = "yuemupicture:listPictureVOByPage:";

    /**
     * 获取用户签到记录的 Redis Key
     * @param year 年份
     * @param userId 用户 id
     * @return 拼接好的 Redis Key
     */
    static String getUserSignInRedisKey(int year, long userId) {
        return String.format("%s:%s:%S", USER_SIGN_IN_REDIS_KEY_PREFIX, year, userId);
    }
}
