package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.mapper.AiTokenRecordMapper;
import com.lumenglover.yuemupicturebackend.model.entity.AiTokenRecord;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.service.AiTokenRecordService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.lumenglover.yuemupicturebackend.manager.websocket.AiTokenWebSocketHandler;
import org.springframework.context.annotation.Lazy;

import javax.annotation.Resource;
import java.util.Set;

/**
 * AI Token 服务实现类
 */
@Service
@Slf4j
public class AiTokenRecordServiceImpl extends ServiceImpl<AiTokenRecordMapper, AiTokenRecord> implements AiTokenRecordService {

    @Resource
    private UserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    @Lazy
    private AiTokenWebSocketHandler aiTokenWebSocketHandler;

    // 缓存前缀
    private static final String LIMIT_5H_PREFIX = "ai:limit:5h:";
    private static final String LIMIT_WEEK_PREFIX = "ai:limit:week:";
    private static final String IMAGE_GEN_WEEK_PREFIX = "ai:image_gen:week:";
    private static final String IMAGE_SEARCH_WEEK_PREFIX = "ai:image_search:week:";

    // 5小时的时间毫秒数
    private static final long FIVE_HOURS_MILLIS = 5 * 60 * 60 * 1000L;

    @Override
    public void checkTokenQuota(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        int memberType = user.getMemberType() != null ? user.getMemberType() : 0;
        boolean isAdmin = userService.isAdmin(user);

        // 获取该用户的额度
        long limit5h = get5hLimit(memberType, isAdmin);
        long limitWeek = getWeekLimit(memberType, isAdmin);

        long now = System.currentTimeMillis();
        String limit5hKey = LIMIT_5H_PREFIX + userId;
        String limitWeekKey = LIMIT_WEEK_PREFIX + userId;

        // 1. 清理 5 小时之前的数据
        long fiveHoursAgo = now - FIVE_HOURS_MILLIS;
        stringRedisTemplate.opsForZSet().removeRangeByScore(limit5hKey, 0, fiveHoursAgo);

        // 2. 统计 5 小时内总消耗
        Set<String> values = stringRedisTemplate.opsForZSet().range(limit5hKey, 0, -1);
        long current5hTokens = 0;
        if (values != null && !values.isEmpty()) {
            for (String val : values) {
                try {
                    String[] parts = val.split("_");
                    current5hTokens += Integer.parseInt(parts[1]);
                } catch (Exception e) {
                    log.error("解析 ZSet value 失败: {}", val, e);
                }
            }
        }

        if (current5hTokens >= limit5h) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "5小时AI请求配额已达上限");
        }

        // 3. 统计本周总消耗
        String weekTokensStr = stringRedisTemplate.opsForValue().get(limitWeekKey);
        long weekTokens = weekTokensStr != null ? Long.parseLong(weekTokensStr) : 0;

        if (weekTokens >= limitWeek) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "本周AI请求配额已达上限");
        }
    }

    @Async
    @Override
    public void recordTokenUsage(Long userId, int consumeToken) {
        if (consumeToken <= 0) {
            return;
        }

        // 1. 写入数据库流水
        AiTokenRecord record = new AiTokenRecord();
        record.setUserId(userId);
        record.setConsumeToken(consumeToken);
        this.save(record);

        // 2. 写入 Redis
        long now = System.currentTimeMillis();
        String limit5hKey = LIMIT_5H_PREFIX + userId;
        String limitWeekKey = LIMIT_WEEK_PREFIX + userId;

        // 唯一值格式: uuid_consumeToken
        String zsetValue = IdUtil.simpleUUID() + "_" + consumeToken;
        stringRedisTemplate.opsForZSet().add(limit5hKey, zsetValue, now);
        // 设置一下 zset 过期时间，避免僵尸数据（比5小时稍微长一点，比如6小时）
        stringRedisTemplate.expire(limit5hKey, 6, java.util.concurrent.TimeUnit.HOURS);

        stringRedisTemplate.opsForValue().increment(limitWeekKey, consumeToken);

        // 3. 推送最新用量给前端
        try {
            aiTokenWebSocketHandler.sendUsageToUser(userId.toString());
        } catch (Exception e) {
            log.error("推送AI Token用量失败", e);
        }
    }

    @Override
    public com.lumenglover.yuemupicturebackend.model.vo.AiTokenUsageVO getTokenUsage(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        int memberType = user.getMemberType() != null ? user.getMemberType() : 0;
        boolean isAdmin = userService.isAdmin(user);

        long limit5h = get5hLimit(memberType, isAdmin);
        long limitWeek = getWeekLimit(memberType, isAdmin);

        long now = System.currentTimeMillis();
        String limit5hKey = LIMIT_5H_PREFIX + userId;
        String limitWeekKey = LIMIT_WEEK_PREFIX + userId;

        // 1. 清理 5 小时之前的数据
        long fiveHoursAgo = now - FIVE_HOURS_MILLIS;
        stringRedisTemplate.opsForZSet().removeRangeByScore(limit5hKey, 0, fiveHoursAgo);

        // 2. 统计 5 小时内总消耗
        Set<String> values = stringRedisTemplate.opsForZSet().range(limit5hKey, 0, -1);
        long current5hTokens = 0;
        if (values != null && !values.isEmpty()) {
            for (String val : values) {
                try {
                    String[] parts = val.split("_");
                    current5hTokens += Integer.parseInt(parts[1]);
                } catch (Exception e) {
                    log.error("解析 ZSet value 失败: {}", val, e);
                }
            }
        }

        // 3. 统计本周总消耗
        String weekTokensStr = stringRedisTemplate.opsForValue().get(limitWeekKey);
        long weekTokens = weekTokensStr != null ? Long.parseLong(weekTokensStr) : 0;

        // 4. 统计本周生图额度及使用量
        com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum quotaEnum =
            com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.getEnumByValue(isAdmin ? 2 : memberType);
        if (quotaEnum == null) {
            quotaEnum = com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.NORMAL;
        }
        long limitImageGenWeek = quotaEnum.getLimitImageGenWeek();

        String imageGenWeekKey = IMAGE_GEN_WEEK_PREFIX + userId;
        String imageGenCountStr = stringRedisTemplate.opsForValue().get(imageGenWeekKey);
        long usedImageGenWeek = imageGenCountStr != null ? Long.parseLong(imageGenCountStr) : 0;

        // 5. 统计本周以图搜图额度及使用量
        long limitImageSearchWeek = quotaEnum.getLimitImageSearchWeek();
        String imageSearchWeekKey = IMAGE_SEARCH_WEEK_PREFIX + userId;
        String imageSearchCountStr = stringRedisTemplate.opsForValue().get(imageSearchWeekKey);
        long usedImageSearchWeek = imageSearchCountStr != null ? Long.parseLong(imageSearchCountStr) : 0;

        com.lumenglover.yuemupicturebackend.model.vo.AiTokenUsageVO vo = new com.lumenglover.yuemupicturebackend.model.vo.AiTokenUsageVO();
        vo.setMemberType(isAdmin ? 2 : memberType);
        vo.setLimit5h(limit5h);
        vo.setUsed5h(current5hTokens);
        vo.setLimitWeek(limitWeek);
        vo.setUsedWeek(weekTokens);
        vo.setLimitImageGenWeek(limitImageGenWeek);
        vo.setUsedImageGenWeek(usedImageGenWeek);
        vo.setLimitImageSearchWeek(limitImageSearchWeek);
        vo.setUsedImageSearchWeek(usedImageSearchWeek);
        return vo;
    }

    private long get5hLimit(int memberType, boolean isAdmin) {
        com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum quotaEnum =
            com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.getEnumByValue(isAdmin ? 2 : memberType);
        if (quotaEnum == null) {
            quotaEnum = com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.NORMAL;
        }
        return quotaEnum.getLimit5h();
    }

    private long getWeekLimit(int memberType, boolean isAdmin) {
        com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum quotaEnum =
            com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.getEnumByValue(isAdmin ? 2 : memberType);
        if (quotaEnum == null) {
            quotaEnum = com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.NORMAL;
        }
        return quotaEnum.getLimitWeek();
    }

    @Override
    public boolean checkAndDeductImageGenQuota(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }

        int memberType = user.getMemberType() != null ? user.getMemberType() : 0;
        boolean isAdmin = userService.isAdmin(user);

        com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum quotaEnum =
            com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.getEnumByValue(isAdmin ? 2 : memberType);
        if (quotaEnum == null) {
            quotaEnum = com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.NORMAL;
        }

        long limit = quotaEnum.getLimitImageGenWeek();

        String weekKey = IMAGE_GEN_WEEK_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(weekKey);
        long count = countStr != null ? Long.parseLong(countStr) : 0;

        if (count >= limit) {
            return false; // 额度已满
        }

        stringRedisTemplate.opsForValue().increment(weekKey);
        // 简单处理，设置7天过期。严格按自然周可以计算当前到周末的时间，这里用7天滚动即可满足大体需求
        if (count == 0) {
            stringRedisTemplate.expire(weekKey, 7, java.util.concurrent.TimeUnit.DAYS);
        }

        // 推送最新用量给前端
        try {
            aiTokenWebSocketHandler.sendUsageToUser(userId.toString());
        } catch (Exception e) {
            log.error("推送AI生图用量失败", e);
        }

        return true;
    }

    @Override
    public boolean checkAndDeductImageSearchQuota(Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return false;
        }

        int memberType = user.getMemberType() != null ? user.getMemberType() : 0;
        boolean isAdmin = userService.isAdmin(user);

        com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum quotaEnum =
            com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.getEnumByValue(isAdmin ? 2 : memberType);
        if (quotaEnum == null) {
            quotaEnum = com.lumenglover.yuemupicturebackend.model.enums.AiTokenQuotaEnum.NORMAL;
        }

        long limit = quotaEnum.getLimitImageSearchWeek();

        String weekKey = IMAGE_SEARCH_WEEK_PREFIX + userId;
        String countStr = stringRedisTemplate.opsForValue().get(weekKey);
        long count = countStr != null ? Long.parseLong(countStr) : 0;

        if (count >= limit) {
            return false; // 额度已满
        }

        stringRedisTemplate.opsForValue().increment(weekKey);
        // 简单处理，设置7天过期
        if (count == 0) {
            stringRedisTemplate.expire(weekKey, 7, java.util.concurrent.TimeUnit.DAYS);
        }

        // 推送最新用量给前端
        try {
            aiTokenWebSocketHandler.sendUsageToUser(userId.toString());
        } catch (Exception e) {
            log.error("推送AI以图搜图用量失败", e);
        }

        return true;
    }
}
