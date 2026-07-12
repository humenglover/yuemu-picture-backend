package com.lumenglover.yuemupicturebackend.manager;

import cn.hutool.core.util.IdUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 扫码登录管理器
 * 用于 APP 扫码登录 PC/Web 端
 */
@Component
public class QrLoginManager {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 二维码有效期：5 分钟
    private static final long QR_EXPIRATION_TIME = 5 * 60;

    // Redis Key 前缀
    private static final String QR_TOKEN_KEY_PREFIX = "qr:login:token:";      // token -> 状态信息
    private static final String QR_USER_KEY_PREFIX = "qr:login:user:";        // token -> userId

    /**
     * 生成二维码 token（PC/Web 端调用）
     * @return 包含 qrToken 和过期时间的 Map
     */
    public Map<String, Object> generateQrToken() {
        // 生成唯一的 token
        String qrToken = IdUtil.fastSimpleUUID();

        // 初始状态为 WAITING（等待扫描）
        stringRedisTemplate.opsForValue().set(
            QR_TOKEN_KEY_PREFIX + qrToken,
            "WAITING",
            QR_EXPIRATION_TIME,
            TimeUnit.SECONDS
        );

        Map<String, Object> result = new HashMap<>();
        result.put("qrToken", qrToken);
        result.put("expireTime", QR_EXPIRATION_TIME);
        return result;
    }

    /**
     * APP 扫码后更新状态为"已扫描"
     * @param qrToken 二维码 token
     * @param userId 扫码用户 ID
     * @return 是否成功
     */
    public boolean scanQrCode(String qrToken, Long userId) {
        String key = QR_TOKEN_KEY_PREFIX + qrToken;
        String status = stringRedisTemplate.opsForValue().get(key);

        // 检查二维码是否存在且状态为 WAITING
        if (status == null) {
            return false; // 二维码不存在或已过期
        }

        if (!"WAITING".equals(status)) {
            return false; // 二维码已被扫描或已确认
        }

        // 更新状态为 SCANNED，保留剩余过期时间
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expire != null && expire > 0) {
            stringRedisTemplate.opsForValue().set(key, "SCANNED", expire, TimeUnit.SECONDS);
            // 存储用户 ID
            stringRedisTemplate.opsForValue().set(
                QR_USER_KEY_PREFIX + qrToken,
                userId.toString(),
                expire,
                TimeUnit.SECONDS
            );
            return true;
        }

        return false;
    }

    /**
     * APP 确认登录
     * @param qrToken 二维码 token
     * @param userId 确认用户 ID
     * @return 是否成功
     */
    public boolean confirmLogin(String qrToken, Long userId) {
        String key = QR_TOKEN_KEY_PREFIX + qrToken;
        String status = stringRedisTemplate.opsForValue().get(key);

        // 检查状态是否为 SCANNED
        if (!"SCANNED".equals(status)) {
            return false;
        }

        // 验证用户 ID 是否匹配
        String storedUserId = stringRedisTemplate.opsForValue().get(QR_USER_KEY_PREFIX + qrToken);
        if (storedUserId == null || !storedUserId.equals(userId.toString())) {
            return false;
        }

        // 更新状态为 CONFIRMED
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expire != null && expire > 0) {
            stringRedisTemplate.opsForValue().set(key, "CONFIRMED:" + userId, expire, TimeUnit.SECONDS);
            return true;
        }

        return false;
    }

    /**
     * APP 取消登录
     * @param qrToken 二维码 token
     * @param userId 取消用户 ID
     * @return 是否成功
     */
    public boolean cancelLogin(String qrToken, Long userId) {
        String key = QR_TOKEN_KEY_PREFIX + qrToken;
        String status = stringRedisTemplate.opsForValue().get(key);

        if (status == null) {
            return false;
        }

        // 验证用户 ID
        String storedUserId = stringRedisTemplate.opsForValue().get(QR_USER_KEY_PREFIX + qrToken);
        if (storedUserId == null || !storedUserId.equals(userId.toString())) {
            return false;
        }

        // 更新状态为 CANCELLED
        Long expire = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (expire != null && expire > 0) {
            stringRedisTemplate.opsForValue().set(key, "CANCELLED", expire, TimeUnit.SECONDS);
            return true;
        }

        return false;
    }

    /**
     * PC/Web 端轮询检查二维码状态
     * @param qrToken 二维码 token
     * @return 状态信息 Map
     */
    public Map<String, Object> checkQrStatus(String qrToken) {
        String key = QR_TOKEN_KEY_PREFIX + qrToken;
        String status = stringRedisTemplate.opsForValue().get(key);

        Map<String, Object> result = new HashMap<>();

        if (status == null) {
            result.put("status", "EXPIRED");
            result.put("message", "二维码已过期");
            return result;
        }

        if ("WAITING".equals(status)) {
            result.put("status", "WAITING");
            result.put("message", "等待扫描");
        } else if ("SCANNED".equals(status)) {
            result.put("status", "SCANNED");
            result.put("message", "已扫描，等待确认");
        } else if (status.startsWith("CONFIRMED:")) {
            // 提取用户 ID
            String userId = status.substring("CONFIRMED:".length());
            result.put("status", "CONFIRMED");
            result.put("userId", Long.parseLong(userId));
            result.put("message", "已确认登录");
        } else if ("CANCELLED".equals(status)) {
            result.put("status", "CANCELLED");
            result.put("message", "用户取消登录");
        }

        return result;
    }

    /**
     * 清除二维码相关数据（登录成功后调用）
     * @param qrToken 二维码 token
     */
    public void removeQrToken(String qrToken) {
        stringRedisTemplate.delete(QR_TOKEN_KEY_PREFIX + qrToken);
        stringRedisTemplate.delete(QR_USER_KEY_PREFIX + qrToken);
    }

    /**
     * 获取扫码用户 ID
     * @param qrToken 二维码 token
     * @return 用户 ID
     */
    public Long getUserIdByToken(String qrToken) {
        String userId = stringRedisTemplate.opsForValue().get(QR_USER_KEY_PREFIX + qrToken);
        return userId != null ? Long.parseLong(userId) : null;
    }
}
