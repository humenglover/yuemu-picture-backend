package com.lumenglover.yuemupicturebackend.manager;

import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.model.vo.AuthorRankingVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 榜单 Redis 管理器
 * 使用 Redis Sorted Set (ZSET) 实现高性能榜单
 */
@Component
@Slf4j
public class RankingRedisManager {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    // Redis Key 前缀
    private static final String RANKING_KEY_PREFIX = "ranking:";
    private static final String RANKING_DETAIL_KEY_PREFIX = "ranking:detail:";
    private static final String CONTENT_RANKING_KEY_PREFIX = "content:ranking:";  // 内容榜单（图片/帖子）

    // 缓存过期时间
    private static final long RANKING_EXPIRE_HOURS = 6;  // 榜单6小时过期
    private static final long DETAIL_EXPIRE_HOURS = 1;   // 详情1小时过期
    private static final long CONTENT_RANKING_EXPIRE_HOURS = 3;  // 内容榜单3小时过期

    /**
     * 构建榜单 Redis Key
     * 格式: ranking:{rankingType}:{timeRange}
     * 例如: ranking:picture:week
     */
    private String buildRankingKey(String rankingType, String timeRange) {
        return RANKING_KEY_PREFIX + rankingType + ":" + timeRange;
    }

    /**
     * 构建榜单详情 Redis Key
     * 格式: ranking:detail:{rankingType}:{timeRange}:{userId}
     */
    private String buildDetailKey(String rankingType, String timeRange, Long userId) {
        return RANKING_DETAIL_KEY_PREFIX + rankingType + ":" + timeRange + ":" + userId;
    }

    /**
     * 添加或更新榜单数据
     * @param rankingType 榜单类型 (picture/post)
     * @param timeRange 时间范围 (day/week/month/all)
     * @param userId 用户ID
     * @param score 榜单分数
     */
    public void addToRanking(String rankingType, String timeRange, Long userId, double score) {
        try {
            String key = buildRankingKey(rankingType, timeRange);
            redisTemplate.opsForZSet().add(key, userId.toString(), score);
            // 设置过期时间
            redisTemplate.expire(key, RANKING_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("添加榜单数据: key={}, userId={}, score={}", key, userId, score);
        } catch (Exception e) {
            log.error("添加榜单数据失败: rankingType={}, timeRange={}, userId={}",
                    rankingType, timeRange, userId, e);
        }
    }

    /**
     * 批量添加榜单数据
     */
    public void batchAddToRanking(String rankingType, String timeRange, Map<Long, Double> userScores) {
        try {
            String key = buildRankingKey(rankingType, timeRange);

            // 转换为 ZSet 需要的格式
            Set<ZSetOperations.TypedTuple<Object>> tuples = userScores.entrySet().stream()
                    .map(entry -> ZSetOperations.TypedTuple.of(
                            (Object) entry.getKey().toString(),
                            entry.getValue()
                    ))
                    .collect(Collectors.toSet());

            // 批量添加
            redisTemplate.opsForZSet().add(key, tuples);
            // 设置过期时间
            redisTemplate.expire(key, RANKING_EXPIRE_HOURS, TimeUnit.HOURS);

            log.info("批量添加榜单数据成功: key={}, 数量={}", key, userScores.size());
        } catch (Exception e) {
            log.error("批量添加榜单数据失败: rankingType={}, timeRange={}",
                    rankingType, timeRange, e);
        }
    }

    /**
     * 获取榜单前N名
     * @param rankingType 榜单类型
     * @param timeRange 时间范围
     * @param limit 返回数量
     * @return 用户ID列表（按排名排序）
     */
    public List<Long> getTopRanking(String rankingType, String timeRange, int limit) {
        try {
            String key = buildRankingKey(rankingType, timeRange);

            // 使用 ZREVRANGE 获取分数从高到低的前N名
            Set<Object> userIds = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);

            if (userIds == null || userIds.isEmpty()) {
                log.debug("榜单数据为空: key={}", key);
                return new ArrayList<>();
            }

            // 转换为 Long 列表
            List<Long> result = userIds.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toList());

            log.debug("获取榜单前{}名: key={}, 实际返回={}", limit, key, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取榜单数据失败: rankingType={}, timeRange={}, limit={}",
                    rankingType, timeRange, limit, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取榜单前N名（带分数）
     * @return Map<userId, score>
     */
    public Map<Long, Double> getTopRankingWithScores(String rankingType, String timeRange, int limit) {
        try {
            String key = buildRankingKey(rankingType, timeRange);

            // 使用 ZREVRANGE WITH SCORES 获取分数从高到低的前N名及其分数
            Set<ZSetOperations.TypedTuple<Object>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

            if (tuples == null || tuples.isEmpty()) {
                log.debug("榜单数据为空: key={}", key);
                return new LinkedHashMap<>();
            }

            // 转换为 Map（保持顺序）
            Map<Long, Double> result = new LinkedHashMap<>();
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                Long userId = Long.parseLong(tuple.getValue().toString());
                Double score = tuple.getScore();
                result.put(userId, score);
            }

            log.debug("获取榜单前{}名(带分数): key={}, 实际返回={}", limit, key, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取榜单数据(带分数)失败: rankingType={}, timeRange={}, limit={}",
                    rankingType, timeRange, limit, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 获取用户在榜单中的排名
     * @return 排名（从1开始），如果不在榜单中返回null
     */
    public Long getUserRanking(String rankingType, String timeRange, Long userId) {
        try {
            String key = buildRankingKey(rankingType, timeRange);

            // 使用 ZREVRANK 获取排名（从0开始）
            Long rank = redisTemplate.opsForZSet().reverseRank(key, userId.toString());

            if (rank == null) {
                log.debug("用户不在榜单中: key={}, userId={}", key, userId);
                return null;
            }

            // 转换为从1开始的排名
            return rank + 1;

        } catch (Exception e) {
            log.error("获取用户排名失败: rankingType={}, timeRange={}, userId={}",
                    rankingType, timeRange, userId, e);
            return null;
        }
    }

    /**
     * 获取用户的榜单分数
     */
    public Double getUserScore(String rankingType, String timeRange, Long userId) {
        try {
            String key = buildRankingKey(rankingType, timeRange);
            return redisTemplate.opsForZSet().score(key, userId.toString());
        } catch (Exception e) {
            log.error("获取用户分数失败: rankingType={}, timeRange={}, userId={}",
                    rankingType, timeRange, userId, e);
            return null;
        }
    }

    /**
     * 缓存榜单详情数据
     */
    public void cacheRankingDetail(String rankingType, String timeRange, Long userId, AuthorRankingVO vo) {
        try {
            String key = buildDetailKey(rankingType, timeRange, userId);
            String json = JSONUtil.toJsonStr(vo);
            redisTemplate.opsForValue().set(key, json, DETAIL_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("缓存榜单详情: key={}", key);
        } catch (Exception e) {
            log.error("缓存榜单详情失败: rankingType={}, timeRange={}, userId={}",
                    rankingType, timeRange, userId, e);
        }
    }

    /**
     * 获取缓存的榜单详情
     */
    public AuthorRankingVO getCachedRankingDetail(String rankingType, String timeRange, Long userId) {
        try {
            String key = buildDetailKey(rankingType, timeRange, userId);
            Object value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                log.debug("榜单详情缓存未命中: key={}", key);
                return null;
            }

            return JSONUtil.toBean(value.toString(), AuthorRankingVO.class);
        } catch (Exception e) {
            log.error("获取榜单详情缓存失败: rankingType={}, timeRange={}, userId={}",
                    rankingType, timeRange, userId, e);
            return null;
        }
    }

    /**
     * 批量缓存榜单详情
     */
    public void batchCacheRankingDetails(String rankingType, String timeRange, List<AuthorRankingVO> voList) {
        try {
            for (AuthorRankingVO vo : voList) {
                cacheRankingDetail(rankingType, timeRange, vo.getUserId(), vo);
            }
            log.info("批量缓存榜单详情成功: rankingType={}, timeRange={}, 数量={}",
                    rankingType, timeRange, voList.size());
        } catch (Exception e) {
            log.error("批量缓存榜单详情失败: rankingType={}, timeRange={}",
                    rankingType, timeRange, e);
        }
    }

    /**
     * 删除榜单缓存
     */
    public void deleteRanking(String rankingType, String timeRange) {
        try {
            String key = buildRankingKey(rankingType, timeRange);
            redisTemplate.delete(key);
            log.info("删除榜单缓存: key={}", key);
        } catch (Exception e) {
            log.error("删除榜单缓存失败: rankingType={}, timeRange={}",
                    rankingType, timeRange, e);
        }
    }

    /**
     * 构建内容榜单 Redis Key
     * 格式: content:ranking:{contentType}:{timeRange}
     * 例如: content:ranking:picture:hot (图片热榜)
     * 例如: content:ranking:post:hot (帖子热榜)
     */
    private String buildContentRankingKey(String contentType, String timeRange) {
        return CONTENT_RANKING_KEY_PREFIX + contentType + ":" + timeRange;
    }

    /**
     * 添加内容到榜单
     * @param contentType 内容类型 (picture/post)
     * @param timeRange 时间范围 (hot/day/week/month)
     * @param contentId 内容ID
     * @param score 榜单分数
     */
    public void addContentToRanking(String contentType, String timeRange, Long contentId, double score) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);
            redisTemplate.opsForZSet().add(key, contentId.toString(), score);
            // 设置过期时间
            redisTemplate.expire(key, CONTENT_RANKING_EXPIRE_HOURS, TimeUnit.HOURS);
            log.debug("添加内容到榜单: key={}, contentId={}, score={}", key, contentId, score);
        } catch (Exception e) {
            log.error("添加内容到榜单失败: contentType={}, timeRange={}, contentId={}",
                    contentType, timeRange, contentId, e);
        }
    }

    /**
     * 批量添加内容到榜单
     */
    public void batchAddContentToRanking(String contentType, String timeRange, Map<Long, Double> contentScores) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);

            // 转换为 ZSet 需要的格式
            Set<ZSetOperations.TypedTuple<Object>> tuples = contentScores.entrySet().stream()
                    .map(entry -> ZSetOperations.TypedTuple.of(
                            (Object) entry.getKey().toString(),
                            entry.getValue()
                    ))
                    .collect(Collectors.toSet());

            // 批量添加
            redisTemplate.opsForZSet().add(key, tuples);
            // 设置过期时间
            redisTemplate.expire(key, CONTENT_RANKING_EXPIRE_HOURS, TimeUnit.HOURS);

            log.info("批量添加内容到榜单成功: key={}, 数量={}", key, contentScores.size());
        } catch (Exception e) {
            log.error("批量添加内容到榜单失败: contentType={}, timeRange={}",
                    contentType, timeRange, e);
        }
    }

    /**
     * 获取内容榜单前N名
     * @param contentType 内容类型
     * @param timeRange 时间范围
     * @param limit 返回数量
     * @return 内容ID列表（按排名排序）
     */
    public List<Long> getTopContentRanking(String contentType, String timeRange, int limit) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);

            // 使用 ZREVRANGE 获取分数从高到低的前N名
            Set<Object> contentIds = redisTemplate.opsForZSet().reverseRange(key, 0, limit - 1);

            if (contentIds == null || contentIds.isEmpty()) {
                log.debug("内容榜单数据为空: key={}", key);
                return new ArrayList<>();
            }

            // 转换为 Long 列表
            List<Long> result = contentIds.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(Collectors.toList());

            log.debug("获取内容榜单前{}名: key={}, 实际返回={}", limit, key, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取内容榜单数据失败: contentType={}, timeRange={}, limit={}",
                    contentType, timeRange, limit, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取内容榜单前N名（带分数）
     * @return Map<contentId, score>
     */
    public Map<Long, Double> getTopContentRankingWithScores(String contentType, String timeRange, int limit) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);

            // 使用 ZREVRANGE WITH SCORES 获取分数从高到低的前N名及其分数
            Set<ZSetOperations.TypedTuple<Object>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, limit - 1);

            if (tuples == null || tuples.isEmpty()) {
                log.debug("内容榜单数据为空: key={}", key);
                return new LinkedHashMap<>();
            }

            // 转换为 Map（保持顺序）
            Map<Long, Double> result = new LinkedHashMap<>();
            for (ZSetOperations.TypedTuple<Object> tuple : tuples) {
                Long contentId = Long.parseLong(tuple.getValue().toString());
                Double score = tuple.getScore();
                result.put(contentId, score);
            }

            log.debug("获取内容榜单前{}名(带分数): key={}, 实际返回={}", limit, key, result.size());
            return result;

        } catch (Exception e) {
            log.error("获取内容榜单数据(带分数)失败: contentType={}, timeRange={}, limit={}",
                    contentType, timeRange, limit, e);
            return new LinkedHashMap<>();
        }
    }

    /**
     * 获取内容在榜单中的排名
     * @return 排名（从1开始），如果不在榜单中返回null
     */
    public Long getContentRanking(String contentType, String timeRange, Long contentId) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);

            // 使用 ZREVRANK 获取排名（从0开始）
            Long rank = redisTemplate.opsForZSet().reverseRank(key, contentId.toString());

            if (rank == null) {
                log.debug("内容不在榜单中: key={}, contentId={}", key, contentId);
                return null;
            }

            // 转换为从1开始的排名
            return rank + 1;

        } catch (Exception e) {
            log.error("获取内容排名失败: contentType={}, timeRange={}, contentId={}",
                    contentType, timeRange, contentId, e);
            return null;
        }
    }

    /**
     * 获取内容的榜单分数
     */
    public Double getContentScore(String contentType, String timeRange, Long contentId) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);
            return redisTemplate.opsForZSet().score(key, contentId.toString());
        } catch (Exception e) {
            log.error("获取内容分数失败: contentType={}, timeRange={}, contentId={}",
                    contentType, timeRange, contentId, e);
            return null;
        }
    }

    /**
     * 删除内容榜单缓存
     */
    public void deleteContentRanking(String contentType, String timeRange) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);
            redisTemplate.delete(key);
            log.info("删除内容榜单缓存: key={}", key);
        } catch (Exception e) {
            log.error("删除内容榜单缓存失败: contentType={}, timeRange={}",
                    contentType, timeRange, e);
        }
    }

    /**
     * 检查内容榜单是否存在
     */
    public boolean contentRankingExists(String contentType, String timeRange) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查内容榜单是否存在失败: contentType={}, timeRange={}",
                    contentType, timeRange, e);
            return false;
        }
    }

    /**
     * 获取内容榜单总数
     */
    public Long getContentRankingSize(String contentType, String timeRange) {
        try {
            String key = buildContentRankingKey(contentType, timeRange);
            return redisTemplate.opsForZSet().size(key);
        } catch (Exception e) {
            log.error("获取内容榜单总数失败: contentType={}, timeRange={}",
                    contentType, timeRange, e);
            return 0L;
        }
    }

    /**
     * 获取榜单总数
     */
    public Long getRankingSize(String rankingType, String timeRange) {
        try {
            String key = buildRankingKey(rankingType, timeRange);
            return redisTemplate.opsForZSet().size(key);
        } catch (Exception e) {
            log.error("获取榜单总数失败: rankingType={}, timeRange={}",
                    rankingType, timeRange, e);
            return 0L;
        }
    }

    /**
     * 检查榜单是否存在
     */
    public boolean rankingExists(String rankingType, String timeRange) {
        try {
            String key = buildRankingKey(rankingType, timeRange);
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查榜单是否存在失败: rankingType={}, timeRange={}",
                    rankingType, timeRange, e);
            return false;
        }
    }
}
