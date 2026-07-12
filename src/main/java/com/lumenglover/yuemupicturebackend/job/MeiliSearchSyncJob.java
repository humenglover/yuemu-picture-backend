package com.lumenglover.yuemupicturebackend.job;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lumenglover.yuemupicturebackend.mapper.PictureMapper;
import com.lumenglover.yuemupicturebackend.mapper.PostMapper;
import com.lumenglover.yuemupicturebackend.mapper.SpaceMapper;
import com.lumenglover.yuemupicturebackend.mapper.UserMapper;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.Post;
import com.lumenglover.yuemupicturebackend.model.entity.Space;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.utils.EmailSenderUtil;
import com.meilisearch.sdk.Client;
import com.meilisearch.sdk.Index;
import com.meilisearch.sdk.model.Settings;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.annotation.PreDestroy;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * MeiliSearch 数据同步任务
 * 负责MySQL与Meilisearch之间的全量/增量数据同步、数据一致性检查、异常告警等功能
 *
 * @author 开发者
 * @date 2026-01
 */
@Component
@Slf4j
public class MeiliSearchSyncJob implements CommandLineRunner {

    // ===================== 常量定义区 =====================
    /**
     * 增量同步时间窗口（分钟）
     */
    private static final long INCREMENTAL_SYNC_INTERVAL_MINUTES = 5L;

    /**
     * 增量同步固定延迟（毫秒）= 5分钟
     */
    private static final long INCREMENTAL_SYNC_DELAY_MILLIS = INCREMENTAL_SYNC_INTERVAL_MINUTES * 60 * 1000;

    /**
     * Meilisearch索引名称常量
     */
    private static final String INDEX_NAME_PICTURE = "picture";
    private static final String INDEX_NAME_USER = "user";
    private static final String INDEX_NAME_POST = "post";
    private static final String INDEX_NAME_SPACE = "space";

    /**
     * 数据一致性检查阈值：数量差异超过该值触发全量同步
     */
    private static final int CONSISTENCY_CHECK_THRESHOLD = 10;

    /**
     * Meilisearch批量操作最大批次大小
     */
    private static final int MAX_BATCH_SIZE = 500;

    /**
     * 批次间休眠时间（毫秒）
     */
    private static final long BATCH_SLEEP_TIME = 100L;

    /**
     * 重试最大等待时间（毫秒）
     */
    private static final long MAX_RETRY_WAIT_TIME = 30000L;

    // ===================== 配置项（可通过配置文件动态调整） =====================
    @Value("${spring.mail.admin:admin@example.com}")
    private String adminEmail;

    @Value("${spring.meilisearch.sync.batch.size:1000}")
    private int batchSize;

    @Value("${spring.meilisearch.sync.alert.interval:1800000}") // 30分钟（毫秒）
    private long alertInterval;

    @Value("${spring.meilisearch.sync.retry.times:3}")
    private int retryTimes;

    @Value("${spring.meilisearch.sync.retry.interval:5000}") // 5秒（毫秒）
    private long retryInterval;

    @Value("${spring.meilisearch.sync.picture.enable:true}")
    private boolean pictureSyncEnabled;

    @Value("${spring.meilisearch.sync.user.enable:true}")
    private boolean userSyncEnabled;

    @Value("${spring.meilisearch.sync.post.enable:true}")
    private boolean postSyncEnabled;

    @Value("${spring.meilisearch.sync.space.enable:true}")
    private boolean spaceSyncEnabled;

    @Value("${spring.meilisearch.sync.incremental.random.delay.max:30}") // 增量同步最大随机延迟（秒）
    private int incrementalRandomDelayMax;

    // ===================== 资源注入区 =====================
    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private SpaceMapper spaceMapper;

    @Resource
    private PostMapper postMapper;

    @Resource
    private Client meiliSearchClient;

    @Resource
    private EmailSenderUtil emailSenderUtil;

    // ===================== 状态与统计区 =====================
    /**
     * 记录最近一次发送告警邮件的时间，避免短时间内发送过多邮件
     */
    private volatile long lastAlertTime = 0L;

    /**
     * 同步成功数统计（按数据类型分类）
     */
    private final Map<String, AtomicInteger> syncSuccessCount = new ConcurrentHashMap<>();

    /**
     * 同步失败数统计（按数据类型分类）
     */
    private final Map<String, AtomicInteger> syncFailureCount = new ConcurrentHashMap<>();

    /**
     * 告警失败计数器（按告警类型分类）
     */
    private final Map<String, AtomicInteger> alertFailureCount = new ConcurrentHashMap<>();

    /**
     * 告警失败阈值
     */
    private static final int ALERT_FAILURE_THRESHOLD = 3;

    /**
     * 同步任务线程池
     */
    private final ExecutorService syncExecutor = Executors.newFixedThreadPool(4);

    // ===================== 定时任务入口 =====================

    /**
     * 每日数据一致性检查（凌晨2点执行）
     * 功能：清理已删除数据 + 检查并修复Meilisearch与MySQL数据一致性
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void dailyConsistencyCheck() {
        try {
            log.info("===== 开始Meilisearch与MySQL数据一致性检查 =====");

            // 清理各类已删除/无效数据
            cleanDeletedPictures();
            cleanDraftPictures();
            cleanDeletedUsers();
            cleanDeletedSpaces();

            // 检查并修复数据一致性
            checkAndFixDataConsistency();

            log.info("===== Meilisearch与MySQL数据一致性检查完成 =====");
        } catch (Exception e) {
            String errorMsg = "Meilisearch数据一致性检查失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch数据一致性检查异常", errorMsg);
        }
    }

    /**
     * 图片增量同步（每5分钟执行，随机延迟0-30秒）
     */
    @Scheduled(fixedDelay = INCREMENTAL_SYNC_DELAY_MILLIS)
    public void incrementalSyncPictures() {
        executeIncrementalSync(this::incrementalSyncPicturesInternal, "picture");
    }

    /**
     * 用户增量同步（每5分钟执行，初始延迟20秒 + 随机延迟0-30秒）
     */
    @Scheduled(fixedDelay = INCREMENTAL_SYNC_DELAY_MILLIS, initialDelay = 20000)
    public void incrementalSyncUsers() {
        executeIncrementalSync(this::incrementalSyncUsersInternal, "user");
    }

    /**
     * 帖子增量同步（每5分钟执行，初始延迟40秒 + 随机延迟0-30秒）
     */
    @Scheduled(fixedDelay = INCREMENTAL_SYNC_DELAY_MILLIS, initialDelay = 40000)
    public void incrementalSyncPosts() {
        executeIncrementalSync(this::incrementalSyncPostsInternal, "post");
    }

    /**
     * 空间增量同步（每5分钟执行，初始延迟40秒 + 随机延迟0-30秒）
     */
    @Scheduled(fixedDelay = INCREMENTAL_SYNC_DELAY_MILLIS, initialDelay = 40000)
    public void incrementalSyncSpaces() {
        executeIncrementalSync(this::incrementalSyncSpacesInternal, "space");
    }

    // ===================== 应用启动执行逻辑 =====================

    /**
     * 应用启动时删除旧索引并执行全量同步
     */
    @Override
    public void run(String... args) {
        try {
            log.info("===== 应用启动，正在检查并配置Meilisearch索引 =====");
            // 只初始化索引配置，不删除已有索引
            initIndices();
            log.info("===== Meilisearch索引配置检查并更新完成 =====");

            // 检查是否需要进行初始化全量同步（如果索引内没有文档，说明是首次部署，则进行全量同步）
            boolean needFullSync = false;
            String[] indices = {INDEX_NAME_PICTURE, INDEX_NAME_USER, INDEX_NAME_POST, INDEX_NAME_SPACE};
            for (String indexName : indices) {
                if (getMeiliSearchDocumentCount(indexName) == 0) {
                    needFullSync = true;
                    log.info("Meilisearch索引 {} 中无文档，标记需要全量同步", indexName);
                    break;
                }
            }

            if (needFullSync) {
                log.info("===== 检测到索引为空，开始Meilisearch初始化全量同步 =====");
                fullSync();
                log.info("===== Meilisearch初始化全量同步完成 =====");
            } else {
                log.info("===== Meilisearch中已有数据，跳过启动全量同步 =====");
            }
        } catch (Exception e) {
            String errorMsg = "应用启动Meilisearch索引检查或初始化同步失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("应用启动Meilisearch同步异常", errorMsg);
        }
    }

    // ===================== 核心同步逻辑 =====================

    /**
     * 全量同步入口（按配置启用不同类型数据同步）
     */
    public void fullSync() {
        try {
            log.info("===== 开始全量同步数据到Meilisearch =====");

            if (pictureSyncEnabled) {
                fullSyncPictures();
            }
            if (userSyncEnabled) {
                fullSyncUsers();
            }
            if (postSyncEnabled) {
                fullSyncPosts();
            }
            if (spaceSyncEnabled) {
                fullSyncSpaces();
            }

            log.info("===== 全量同步数据到Meilisearch完成 =====");
        } catch (Exception e) {
            String errorMsg = "全量同步Meilisearch失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch全量同步异常", errorMsg);
            throw e;
        }
    }

    /**
     * 全量同步图片数据
     * 同步范围：未删除、非草稿、公共空间（spaceId为空）的图片
     */
    private void fullSyncPictures() {
        try {
            // 1. 查询MySQL中符合条件的总记录数
            long totalCount = pictureMapper.selectCount(
                    new QueryWrapper<Picture>()
                            .eq("isDelete", 0)
                            .eq("isDraft", 0)
                            .isNull("spaceId")
            );

            log.info("开始全量同步图片数据，符合条件总数：{}", totalCount);

            if (totalCount == 0) {
                log.info("图片全量同步：无符合条件数据，直接返回");
                return;
            }

            // 2. 计算总页数
            long totalPages = calculateTotalPages(totalCount, batchSize);

            // 3. 分页并发同步
            syncByPage(
                    totalPages,
                    batchSize,
                    INDEX_NAME_PICTURE,
                    this::buildPictureQueryWrapper,
                    pictureMapper::selectPage,
                    this::buildPictureIndexQuery
            );

            // 4. 清理无效数据
            cleanDeletedPictures();
            cleanDraftPictures();

            log.info("图片全量同步完成");
        } catch (Exception e) {
            String errorMsg = "全量同步图片数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch图片全量同步异常", errorMsg);
            throw e;
        }
    }

    /**
     * 全量同步用户数据
     * 同步范围：未删除、未被封禁的用户
     */
    private void fullSyncUsers() {
        try {
            // 1. 查询MySQL中符合条件的总记录数
            long totalCount = userMapper.selectCount(
                    new QueryWrapper<User>()
                            .eq("isDelete", 0)
                            .ne("userRole", "ban")
            );

            log.info("开始全量同步用户数据，符合条件总数：{}", totalCount);

            if (totalCount == 0) {
                log.info("用户全量同步：无符合条件数据，直接返回");
                return;
            }

            // 2. 计算总页数
            long totalPages = calculateTotalPages(totalCount, batchSize);

            // 3. 分页并发同步
            syncByPage(
                    totalPages,
                    batchSize,
                    INDEX_NAME_USER,
                    this::buildUserQueryWrapper,
                    userMapper::selectPage,
                    this::buildUserIndexQuery
            );

            // 4. 清理无效数据
            cleanDeletedUsers();

            log.info("用户全量同步完成");
        } catch (Exception e) {
            String errorMsg = "全量同步用户数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch用户全量同步异常", errorMsg);
            throw e;
        }
    }

    /**
     * 全量同步帖子数据
     * 同步范围：未删除、审核通过的帖子
     */
    private void fullSyncPosts() {
        try {
            // 1. 查询MySQL中符合条件的总记录数
            long totalCount = postMapper.selectCount(
                    new QueryWrapper<Post>()
                            .eq("isDelete", 0)
                            .eq("status", 1)
                            .eq("isDraft", 0) // 只同步非草稿的帖子
            );

            log.info("开始全量同步帖子数据，符合条件总数：{}", totalCount);

            if (totalCount == 0) {
                log.info("帖子全量同步：无符合条件数据，直接返回");
                return;
            }

            // 2. 计算总页数
            long totalPages = calculateTotalPages(totalCount, batchSize);

            // 3. 分页并发同步
            syncByPage(
                    totalPages,
                    batchSize,
                    INDEX_NAME_POST,
                    this::buildPostQueryWrapper,
                    postMapper::selectPage,
                    this::buildPostIndexQuery
            );

            log.info("帖子全量同步完成");
        } catch (Exception e) {
            String errorMsg = "全量同步帖子数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch帖子全量同步异常", errorMsg);
            throw e;
        }
    }

    /**
     * 全量同步空间数据
     * 同步范围：未删除、团队空间（spaceType=1）
     */
    private void fullSyncSpaces() {
        try {
            // 1. 查询MySQL中符合条件的总记录数
            long totalCount = spaceMapper.selectCount(
                    new QueryWrapper<Space>()
                            .eq("isDelete", 0)
                            .eq("spaceType", 1)
            );

            log.info("开始全量同步空间数据，符合条件总数：{}", totalCount);

            if (totalCount == 0) {
                log.info("空间全量同步：无符合条件数据，直接返回");
                return;
            }

            // 2. 计算总页数
            long totalPages = calculateTotalPages(totalCount, batchSize);

            // 3. 分页并发同步
            syncByPage(
                    totalPages,
                    batchSize,
                    INDEX_NAME_SPACE,
                    this::buildSpaceQueryWrapper,
                    spaceMapper::selectPage,
                    this::buildSpaceIndexQuery
            );

            // 4. 清理无效数据
            cleanDeletedSpaces();

            log.info("空间全量同步完成");
        } catch (Exception e) {
            String errorMsg = "全量同步空间数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch空间全量同步异常", errorMsg);
            throw e;
        }
    }

    // ===================== 增量同步内部逻辑 =====================

    /**
     * 图片增量同步内部逻辑
     * 处理范围：
     * 1. 最近5分钟更新的有效图片（未删除、审核通过、非草稿、公共空间）
     * 2. 最近5分钟删除的图片
     * 3. 最近5分钟审核未通过的图片（从Meilisearch删除）
     * 4. 最近5分钟审核通过的图片（添加到Meilisearch）
     */
    private void incrementalSyncPicturesInternal() {
        Date syncTime = calculateSyncTime();

        try {
            // 1. 同步新增/更新的有效图片
            syncIncrementalValidData(
                    syncTime,
                    this::buildPictureIncrementalQueryWrapper,
                    pictureMapper::selectList,
                    INDEX_NAME_PICTURE,
                    this::buildPictureIndexQuery,
                    "picture"
            );

            // 2. 删除已删除的图片
            cleanIncrementalDeletedData(
                    syncTime,
                    pictureMapper::selectDeletedPicturesByUpdateTime,
                    INDEX_NAME_PICTURE,
                    "picture"
            );

            // 3. 删除审核未通过的图片
            cleanIncrementalUnreviewedData(
                    syncTime,
                    this::buildPictureUnreviewedQueryWrapper,
                    pictureMapper::selectList,
                    INDEX_NAME_PICTURE,
                    "picture"
            );

            // 4. 同步审核通过的图片
            syncIncrementalReviewedData(
                    syncTime,
                    this::buildPictureReviewedQueryWrapper,
                    pictureMapper::selectList,
                    INDEX_NAME_PICTURE,
                    this::buildPictureIndexQuery,
                    "picture"
            );
        } catch (Exception e) {
            String errorMsg = "增量同步图片数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch图片增量同步异常", errorMsg);
        }
    }

    /**
     * 用户增量同步内部逻辑
     * 处理范围：
     * 1. 最近5分钟更新的有效用户（未删除、未封禁）
     * 2. 最近5分钟删除的用户
     * 3. 最近5分钟被封禁的用户（从Meilisearch删除）
     */
    private void incrementalSyncUsersInternal() {
        Date syncTime = calculateSyncTime();

        try {
            // 1. 同步新增/更新的有效用户
            syncIncrementalValidData(
                    syncTime,
                    this::buildUserIncrementalQueryWrapper,
                    userMapper::selectList,
                    INDEX_NAME_USER,
                    this::buildUserIndexQuery,
                    "user"
            );

            // 2. 删除已删除的用户
            cleanIncrementalDeletedData(
                    syncTime,
                    userMapper::selectDeletedUsersByUpdateTime,
                    INDEX_NAME_USER,
                    "user"
            );

            // 3. 删除被封禁的用户
            cleanIncrementalBannedData(
                    syncTime,
                    this::buildUserBannedQueryWrapper,
                    userMapper::selectList,
                    INDEX_NAME_USER,
                    "user"
            );
        } catch (Exception e) {
            String errorMsg = "增量同步用户数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch用户增量同步异常", errorMsg);
        }
    }

    /**
     * 帖子增量同步内部逻辑
     * 处理范围：
     * 1. 最近5分钟更新的有效帖子（未删除、审核通过）
     * 2. 最近5分钟删除的帖子
     * 3. 最近5分钟审核未通过的帖子（从Meilisearch删除）
     */
    private void incrementalSyncPostsInternal() {
        Date syncTime = calculateSyncTime();

        try {
            // 1. 同步新增/更新的有效帖子
            syncIncrementalValidData(
                    syncTime,
                    this::buildPostIncrementalQueryWrapper,
                    postMapper::selectList,
                    INDEX_NAME_POST,
                    this::buildPostIndexQuery,
                    "post"
            );

            // 2. 删除已删除的帖子
            cleanIncrementalDeletedData(
                    syncTime,
                    postMapper::selectDeletedPostsByUpdateTime,
                    INDEX_NAME_POST,
                    "post"
            );

            // 3. 删除审核未通过的帖子
            cleanIncrementalUnreviewedData(
                    syncTime,
                    this::buildPostUnreviewedQueryWrapper,
                    postMapper::selectList,
                    INDEX_NAME_POST,
                    "post"
            );
        } catch (Exception e) {
            String errorMsg = "增量同步帖子数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch帖子增量同步异常", errorMsg);
        }
    }

    /**
     * 空间增量同步内部逻辑
     * 处理范围：
     * 1. 最近5分钟更新的有效空间（未删除、团队空间）
     * 2. 最近5分钟删除的空间
     */
    private void incrementalSyncSpacesInternal() {
        Date syncTime = calculateSyncTime();

        try {
            // 1. 同步新增/更新的有效空间
            syncIncrementalValidData(
                    syncTime,
                    this::buildSpaceIncrementalQueryWrapper,
                    spaceMapper::selectList,
                    INDEX_NAME_SPACE,
                    this::buildSpaceIndexQuery,
                    "space"
            );

            // 2. 删除已删除的空间
            cleanIncrementalDeletedData(
                    syncTime,
                    spaceMapper::selectDeletedSpacesByUpdateTime,
                    INDEX_NAME_SPACE,
                    "space"
            );
        } catch (Exception e) {
            String errorMsg = "增量同步空间数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch空间增量同步异常", errorMsg);
        }
    }

    // ===================== 数据清理逻辑 =====================

    /**
     * 清理Meilisearch中已删除的图片数据
     */
    private void cleanDeletedPictures() {
        cleanDeletedData(
                pictureMapper::selectAllDeletedPictures,
                INDEX_NAME_PICTURE,
                "picture"
        );
    }

    /**
     * 清理Meilisearch中草稿状态的图片数据
     */
    private void cleanDraftPictures() {
        try {
            // 查询所有草稿状态的图片
            List<Picture> draftPictures = pictureMapper.selectList(
                    new QueryWrapper<Picture>().eq("isDraft", 1)
            );

            if (draftPictures.isEmpty()) {
                log.info("清理Meilisearch草稿图片：无草稿图片数据");
                return;
            }

            log.info("准备清理Meilisearch中草稿状态的图片，数量：{}", draftPictures.size());

            // 执行批量删除
            batchDeleteFromMeili(
                    draftPictures.stream().map(Picture::getId).map(String::valueOf).collect(Collectors.toList()),
                    INDEX_NAME_PICTURE,
                    "draft_picture"
            );

        } catch (Exception e) {
            String errorMsg = "清理Meilisearch中草稿状态的图片数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch草稿图片清理异常", errorMsg);
        }
    }

    /**
     * 清理Meilisearch中已删除的用户数据
     */
    private void cleanDeletedUsers() {
        cleanDeletedData(
                userMapper::selectAllDeletedUsers,
                INDEX_NAME_USER,
                "user"
        );
    }

    /**
     * 清理Meilisearch中已删除的空间数据
     */
    private void cleanDeletedSpaces() {
        cleanDeletedData(
                spaceMapper::selectAllDeletedSpaces,
                INDEX_NAME_SPACE,
                "space"
        );
    }

    // ===================== 数据一致性检查逻辑 =====================

    /**
     * 数据一致性检查与修复入口
     */
    private void checkAndFixDataConsistency() {
        try {
            checkAndFixPictureConsistency();
            checkAndFixUserConsistency();
            checkAndFixPostConsistency();
            checkAndFixSpaceConsistency();
        } catch (Exception e) {
            String errorMsg = "数据一致性检查与修复失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch数据一致性检查异常", errorMsg);
        }
    }

    /**
     * 检查并修复图片数据一致性
     */
    private void checkAndFixPictureConsistency() {
        checkAndFixDataConsistency(
                () -> pictureMapper.selectCount(buildPictureQueryWrapper()),
                INDEX_NAME_PICTURE,
                this::fullSyncPictures,
                "picture"
        );
    }

    /**
     * 检查并修复用户数据一致性
     */
    private void checkAndFixUserConsistency() {
        checkAndFixDataConsistency(
                () -> userMapper.selectCount(buildUserQueryWrapper()),
                INDEX_NAME_USER,
                this::fullSyncUsers,
                "user"
        );
    }

    /**
     * 检查并修复帖子数据一致性
     */
    private void checkAndFixPostConsistency() {
        checkAndFixDataConsistency(
                () -> postMapper.selectCount(buildPostQueryWrapper()),
                INDEX_NAME_POST,
                this::fullSyncPosts,
                "post"
        );
    }

    /**
     * 检查并修复空间数据一致性
     */
    private void checkAndFixSpaceConsistency() {
        checkAndFixDataConsistency(
                () -> spaceMapper.selectCount(buildSpaceQueryWrapper()),
                INDEX_NAME_SPACE,
                this::fullSyncSpaces,
                "space"
        );
    }

    // ===================== 通用工具方法 =====================

    /**
     * 执行增量同步（封装通用逻辑：随机延迟 + 日志 + 异常处理）
     *
     * @param syncLogic 增量同步具体逻辑
     * @param dataTypeName 数据类型名称（用于日志）
     */
    private void executeIncrementalSync(Runnable syncLogic, String dataTypeName) {
        try {
            // 随机延迟，避免任务集中执行
            long randomDelay = RandomUtil.randomInt(0, incrementalRandomDelayMax) * 1000L;
            Thread.sleep(randomDelay);

            log.info("===== 开始增量同步{}数据到Meilisearch（延迟{}秒） =====", dataTypeName, randomDelay / 1000);
            syncLogic.run();
            log.info("===== 增量同步{}数据到Meilisearch完成 =====", dataTypeName);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{}增量同步被中断", dataTypeName, e);
        } catch (Exception e) {
            String errorMsg = "增量同步" + dataTypeName + "数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch" + dataTypeName + "增量同步异常", errorMsg);
        }
    }

    /**
     * 计算总页数
     *
     * @param totalCount 总记录数
     * @param pageSize 每页大小
     * @return 总页数
     */
    private long calculateTotalPages(long totalCount, int pageSize) {
        return (totalCount + pageSize - 1) / pageSize;
    }

    /**
     * 计算增量同步的时间阈值（当前时间 - 5分钟）
     *
     * @return 时间阈值
     */
    private Date calculateSyncTime() {
        return new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(INCREMENTAL_SYNC_INTERVAL_MINUTES));
    }

    /**
     * 分页并发同步数据（通用方法）
     *
     * @param totalPages 总页数
     * @param pageSize 每页大小
     * @param indexName Meilisearch索引名
     * @param queryWrapperBuilder 查询条件构建器
     * @param pageQuery 分页查询方法
     * @param documentBuilder 属性提取器
     * @param <T> 数据实体类型
     */
    private <T> void syncByPage(
            long totalPages,
            int pageSize,
            String indexName,
            java.util.function.Supplier<QueryWrapper<T>> queryWrapperBuilder,
            java.util.function.BiFunction<Page<T>, QueryWrapper<T>, IPage<T>> pageQuery,
            java.util.function.Function<T, Object> documentBuilder
    ) {
        // 边缘场景处理：检查参数是否为空
        if (totalPages <= 0 || pageSize <= 0 || indexName == null || queryWrapperBuilder == null ||
                pageQuery == null || documentBuilder == null) {
            log.warn("全量同步分页参数异常，跳过同步：totalPages={}, pageSize={}, indexName={}", totalPages, pageSize, indexName);
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (long pageNum = 1; pageNum <= totalPages; pageNum++) {
            final long currentPage = pageNum;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // 边缘场景处理：检查当前页码是否有效
                    if (currentPage <= 0) {
                        log.warn("无效页码：{}，跳过此页同步", currentPage);
                        return;
                    }

                    // 分页查询数据
                    Page<T> page = new Page<>(currentPage, pageSize);
                    QueryWrapper<T> queryWrapper = queryWrapperBuilder.get();

                    // 边缘场景处理：检查queryWrapper是否为空
                    if (queryWrapper == null) {
                        log.warn("{}同步第{}页：查询条件为空，跳过此页", indexName, currentPage);
                        return;
                    }

                    IPage<T> dataPage = pageQuery.apply(page, queryWrapper);

                    // 边缘场景处理：检查dataPage是否为空
                    if (dataPage == null) {
                        log.warn("{}同步第{}页：查询结果为空，跳过此页", indexName, currentPage);
                        return;
                    }

                    List<T> dataList = dataPage.getRecords();

                    if (dataList == null || dataList.isEmpty()) {
                        log.info("{}同步第{}页：无数据", indexName, currentPage);
                        return;
                    }

                    // 构建索引文档列表
                    List<Object> docs = dataList.stream()
                            .map(documentBuilder)
                            .collect(Collectors.toList());

                    List<Map<String, Object>> meiliDocs = convertToMeiliDocs(docs);

                    if (meiliDocs == null || meiliDocs.isEmpty()) {
                        log.info("{}同步第{}页：构建的文档为空，跳过此页", indexName, currentPage);
                        return;
                    }

                    // 使用批量处理方法同步到Meilisearch
                    processInBatches(meiliDocs, MAX_BATCH_SIZE, batch -> {
                        retryOperation(() -> {
                            String json = JSONUtil.toJsonStr(batch);
                            meiliSearchClient.index(indexName).addDocuments(json);
                            return null;
                        }, "批量索引" + indexName + "数据到Meilisearch", indexName);
                    });

                    log.info("{}同步第{}页完成，数量：{}", indexName, currentPage, dataList.size());

                    // 主动清理，帮助GC
                    docs.clear();
                    meiliDocs.clear();
                } catch (Exception e) {
                    log.error("{}同步第{}页失败: {}", indexName, currentPage, e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }, syncExecutor);

            futures.add(future);
        }

        // 等待所有分页任务完成
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }
    }

    /**
     * 同步增量有效数据（通用方法）
     *
     * @param syncTime 同步时间阈值
     * @param queryWrapperBuilder 查询条件构建器
     * @param dataQuery 数据查询方法
     * @param indexName Meilisearch索引名
     * @param documentBuilder 文档构建器
     * @param dataTypeName 数据类型名称
     * @param <T> 数据实体类型
     */
    private <T> void syncIncrementalValidData(
            Date syncTime,
            java.util.function.Function<Date, QueryWrapper<T>> queryWrapperBuilder,
            java.util.function.Function<QueryWrapper<T>, List<T>> dataQuery,
            String indexName,
            java.util.function.Function<T, Object> documentBuilder,
            String dataTypeName
    ) {
        // 构建查询条件
        QueryWrapper<T> queryWrapper = queryWrapperBuilder.apply(syncTime);
        List<T> dataList = dataQuery.apply(queryWrapper);

        if (dataList.isEmpty()) {
            log.info("{}增量同步：无需要同步的有效{}", dataTypeName, dataTypeName);
            return;
        }

        log.info("{}增量同步：准备同步有效{}，数量：{}", dataTypeName, dataTypeName, dataList.size());

        List<Object> docs = dataList.stream()
                .map(documentBuilder)
                .collect(Collectors.toList());

        List<Map<String, Object>> meiliDocs = convertToMeiliDocs(docs);

        // 使用批量处理方法同步到Meilisearch
        processInBatches(meiliDocs, MAX_BATCH_SIZE, batch -> {
            retryOperation(() -> {
                String json = JSONUtil.toJsonStr(batch);
                meiliSearchClient.index(indexName).addDocuments(json);
                return null;
            }, "批量索引增量" + dataTypeName + "数据到Meilisearch", indexName);
        });

        log.info("{}增量同步：成功同步有效{}，数量：{}", dataTypeName, dataTypeName, dataList.size());

        // 主动清理
        docs.clear();
        meiliDocs.clear();
    }

    /**
     * 清理增量删除数据（通用方法）
     *
     * @param syncTime 同步时间阈值
     * @param deletedDataQuery 已删除数据查询方法
     * @param indexName Meilisearch索引名
     * @param dataTypeName 数据类型名称
     * @param <T> 数据实体类型
     */
    private <T> void cleanIncrementalDeletedData(
            Date syncTime,
            java.util.function.Function<Date, List<T>> deletedDataQuery,
            String indexName,
            String dataTypeName
    ) {
        List<T> deletedDataList = deletedDataQuery.apply(syncTime);

        if (deletedDataList.isEmpty()) {
            log.info("{}增量同步：无需要删除的{}", dataTypeName, dataTypeName);
            return;
        }

        log.info("{}增量同步：准备删除已删除的{}，数量：{}", dataTypeName, dataTypeName, deletedDataList.size());

        // 提取ID并执行删除
        List<String> idsToDelete = extractIds(deletedDataList);
        int failCount = batchDeleteFromMeili(idsToDelete, indexName, dataTypeName);

        // 失败率超10%发送告警
        checkAndSendDeleteAlert(idsToDelete.size(), failCount, dataTypeName, "增量清理");
    }

    /**
     * 清理增量审核未通过数据（通用方法）
     *
     * @param syncTime 同步时间阈值
     * @param queryWrapperBuilder 查询条件构建器
     * @param dataQuery 数据查询方法
     * @param indexName Meilisearch索引名
     * @param dataTypeName 数据类型名称
     * @param <T> 数据实体类型
     */
    private <T> void cleanIncrementalUnreviewedData(
            Date syncTime,
            java.util.function.Function<Date, QueryWrapper<T>> queryWrapperBuilder,
            java.util.function.Function<QueryWrapper<T>, List<T>> dataQuery,
            String indexName,
            String dataTypeName
    ) {
        QueryWrapper<T> queryWrapper = queryWrapperBuilder.apply(syncTime);
        List<T> unreviewedDataList = dataQuery.apply(queryWrapper);

        if (unreviewedDataList.isEmpty()) {
            log.info("{}增量同步：无需要删除的审核未通过{}", dataTypeName, dataTypeName);
            return;
        }

        log.info("{}增量同步：准备删除审核未通过的{}，数量：{}", dataTypeName, dataTypeName, unreviewedDataList.size());

        // 提取ID并执行删除
        List<String> idsToDelete = extractIds(unreviewedDataList);
        int failCount = batchDeleteFromMeili(idsToDelete, indexName, dataTypeName);

        // 失败率超10%发送告警
        checkAndSendDeleteAlert(idsToDelete.size(), failCount, dataTypeName, "审核状态清理");
    }

    /**
     * 清理增量被封禁数据（通用方法）
     *
     * @param syncTime 同步时间阈值
     * @param queryWrapperBuilder 查询条件构建器
     * @param dataQuery 数据查询方法
     * @param indexName Meilisearch索引名
     * @param dataTypeName 数据类型名称
     * @param <T> 数据实体类型
     */
    private <T> void cleanIncrementalBannedData(
            Date syncTime,
            java.util.function.Function<Date, QueryWrapper<T>> queryWrapperBuilder,
            java.util.function.Function<QueryWrapper<T>, List<T>> dataQuery,
            String indexName,
            String dataTypeName
    ) {
        QueryWrapper<T> queryWrapper = queryWrapperBuilder.apply(syncTime);
        List<T> bannedDataList = dataQuery.apply(queryWrapper);

        if (bannedDataList.isEmpty()) {
            log.info("{}增量同步：无需要删除的被封禁{}", dataTypeName, dataTypeName);
            return;
        }

        log.info("{}增量同步：准备删除被封禁的{}，数量：{}", dataTypeName, dataTypeName, bannedDataList.size());

        // 提取ID并执行删除
        List<String> idsToDelete = extractIds(bannedDataList);
        int failCount = batchDeleteFromMeili(idsToDelete, indexName, dataTypeName);

        // 失败率超10%发送告警
        checkAndSendDeleteAlert(idsToDelete.size(), failCount, dataTypeName, "封禁状态清理");
    }

    /**
     * 同步增量审核通过数据（通用方法）
     *
     * @param syncTime 同步时间阈值
     * @param queryWrapperBuilder 查询条件构建器
     * @param dataQuery 数据查询方法
     * @param indexName Meilisearch索引名
     * @param documentBuilder 文档构建器
     * @param dataTypeName 数据类型名称
     * @param <T> 数据实体类型
     */
    private <T> void syncIncrementalReviewedData(
            Date syncTime,
            java.util.function.Function<Date, QueryWrapper<T>> queryWrapperBuilder,
            java.util.function.Function<QueryWrapper<T>, List<T>> dataQuery,
            String indexName,
            java.util.function.Function<T, Object> documentBuilder,
            String dataTypeName
    ) {
        QueryWrapper<T> queryWrapper = queryWrapperBuilder.apply(syncTime);
        List<T> reviewedDataList = dataQuery.apply(queryWrapper);

        if (reviewedDataList.isEmpty()) {
            log.info("{}增量同步：无需要添加的审核通过{}", dataTypeName, dataTypeName);
            return;
        }

        log.info("{}增量同步：准备添加审核通过的{}，数量：{}", dataTypeName, dataTypeName, reviewedDataList.size());

        // 构建索引文档
        List<Object> docs = reviewedDataList.stream()
                .map(documentBuilder)
                .collect(Collectors.toList());

        List<Map<String, Object>> meiliDocs = convertToMeiliDocs(docs);

        // 使用批量处理方法同步到Meilisearch
        processInBatches(meiliDocs, MAX_BATCH_SIZE, batch -> {
            retryOperation(() -> {
                String json = JSONUtil.toJsonStr(batch);
                meiliSearchClient.index(indexName).addDocuments(json);
                return null;
            }, "批量索引审核通过的" + dataTypeName + "数据到Meilisearch", indexName);
        });

        int failCount = 0; // 默认成功

        log.info("{}增量同步：成功添加审核通过的{}，总数：{}，成功：{}，失败：{}",
                dataTypeName, dataTypeName, reviewedDataList.size(), reviewedDataList.size() - failCount, failCount);

        // 失败率超10%发送告警
        checkAndSendDeleteAlert(reviewedDataList.size(), failCount, dataTypeName, "审核状态添加");
    }

    /**
     * 清理已删除数据（通用方法）
     *
     * @param deletedDataQuery 已删除数据查询方法
     * @param indexName Meilisearch索引名
     * @param dataTypeName 数据类型名称
     * @param <T> 数据实体类型
     */
    private <T> void cleanDeletedData(
            java.util.function.Supplier<List<T>> deletedDataQuery,
            String indexName,
            String dataTypeName
    ) {
        try {
            List<T> deletedDataList = deletedDataQuery.get();

            if (deletedDataList.isEmpty()) {
                log.info("清理Meilisearch{}：无已删除{}数据", dataTypeName, dataTypeName);
                return;
            }

            log.info("准备清理Meilisearch中已删除的{}，数量：{}", dataTypeName, deletedDataList.size());

            // 提取ID并执行删除
            List<String> idsToDelete = extractIds(deletedDataList);
            int failCount = batchDeleteFromMeili(idsToDelete, indexName, dataTypeName);

            // 失败率超10%发送告警
            checkAndSendDeleteAlert(idsToDelete.size(), failCount, dataTypeName, "全量清理");

        } catch (Exception e) {
            String errorMsg = "清理已删除的" + dataTypeName + "数据失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch" + dataTypeName + "清理异常", errorMsg);
        }
    }

    /**
     * 批量从Meilisearch删除数据（通用方法）
     *
     * @param idsToDelete 待删除ID列表
     * @param indexName Meilisearch索引名
     * @param dataTypeName 数据类型名称
     * @return 失败数量
     */
    private int batchDeleteFromMeili(List<String> idsToDelete, String indexName, String dataTypeName) {
        int successCount = 0;
        int failCount = 0;

        try {
            // 使用批量处理方法进行删除
            processInBatches(idsToDelete, MAX_BATCH_SIZE, batch -> {
                retryOperation(() -> {
                    meiliSearchClient.index(indexName).deleteDocuments(batch);
                    return null;
                }, "批量从Meilisearch删除" + dataTypeName, indexName);
            });

            successCount = idsToDelete.size();
        } catch (Exception e) {
            log.error("批量从Meilisearch删除{}失败, 数量: {}: {}", dataTypeName, idsToDelete.size(), e.getMessage(), e);

            // 批量删除失败，逐个删除
            for (String id : idsToDelete) {
                try {
                    retryOperation(() -> {
                        meiliSearchClient.index(indexName).deleteDocument(id);
                        return null;
                    }, "从Meilisearch删除" + dataTypeName, indexName);
                    successCount++;
                } catch (Exception ex) {
                    log.error("从Meilisearch删除{}失败, ID: {}: {}", dataTypeName, id, ex.getMessage(), ex);
                    failCount++;
                }
            }
        }

        log.info("清理Meilisearch中{}完成, 成功：{}，失败：{}", dataTypeName, successCount, failCount);
        return failCount;
    }

    /**
     * 检查并发送删除失败告警
     *
     * @param totalCount 总数
     * @param failCount 失败数
     * @param dataTypeName 数据类型名称
     * @param operationType 操作类型
     */
    private void checkAndSendDeleteAlert(int totalCount, int failCount, String dataTypeName, String operationType) {
        if (failCount > 0 && failCount > totalCount * 0.1) {
            String alertContent = String.format(
                    "Meilisearch%s%s失败数量过多：\n总数：%d\n失败：%d",
                    dataTypeName, operationType, totalCount, failCount
            );
            sendAlertEmail("Meilisearch" + dataTypeName + "增量清理异常", alertContent);
        }
    }

    /**
     * 检查并修复数据一致性（通用方法）
     *
     * @param mysqlCountSupplier MySQL数量查询方法
     * @param indexName Meilisearch索引名
     * @param fullSyncFunc 全量同步方法
     * @param dataTypeName 数据类型名称
     */
    private void checkAndFixDataConsistency(
            java.util.function.Supplier<Long> mysqlCountSupplier,
            String indexName,
            Runnable fullSyncFunc,
            String dataTypeName
    ) {
        try {
            // 1. 获取MySQL和Meilisearch的数量
            long mysqlCount = mysqlCountSupplier.get();
            long esCount = getMeiliSearchDocumentCount(indexName);

            // 2. 检查数量差异
            boolean needFullSync = Math.abs(mysqlCount - esCount) > CONSISTENCY_CHECK_THRESHOLD;

            // 4. 需要全量同步则执行
            if (needFullSync) {
                log.warn("{}数据数量不一致：MySQL={}, Meilisearch={}, 差异={}条，开始全量同步修复",
                        dataTypeName, mysqlCount, esCount, Math.abs(mysqlCount - esCount));
                fullSyncFunc.run();
            }

        } catch (Exception e) {
            String errorMsg = "检查" + dataTypeName + "数据一致性失败: " + e.getMessage();
            log.error(errorMsg, e);
            sendAlertEmail("Meilisearch" + dataTypeName + "一致性检查异常", errorMsg);
        }
    }

    /**
     * 重试操作 - 使用指数退避策略
     *
     * @param operation 待重试的操作
     * @param operationName 操作名称（用于日志）
     * @param dataType 数据类型（用于统计）
     * @param <T> 返回值类型
     * @return 操作执行结果
     */
    private <T> T retryOperation(java.util.function.Supplier<T> operation, String operationName, String dataType) {
        Exception lastException = null;

        for (int retryNum = 0; retryNum < retryTimes; retryNum++) {
            try {
                T result = operation.get();
                syncSuccessCount.computeIfAbsent(dataType, k -> new AtomicInteger()).incrementAndGet();
                return result;
            } catch (Exception e) {
                lastException = e;

                if (isRetryableException(e)) {
                    log.warn("{}失败，第{}次重试，原因: {}", operationName, retryNum + 1, e.getMessage());

                    // 非最后一次重试，执行指数退避
                    if (retryNum < retryTimes - 1) {
                        try {
                            long exponentialBackoff = retryInterval * (long) Math.pow(2, retryNum);
                            // 限制最大等待时间，避免总重试等待时间过长
                            long actualSleepTime = Math.min(exponentialBackoff, MAX_RETRY_WAIT_TIME);
                            Thread.sleep(actualSleepTime);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("重试被中断", ie);
                        }
                    }
                } else {
                    log.error("{}遇到不可重试异常，停止重试，原因: {}", operationName, e.getMessage());
                    break;
                }
            }
        }

        // 记录失败统计
        syncFailureCount.computeIfAbsent(dataType, k -> new AtomicInteger()).incrementAndGet();

        String errorMsg = String.format("重试%d次后仍然失败: %s，数据类型：%s", retryTimes, operationName, dataType);
        log.error(errorMsg, lastException);

        // 发送告警邮件
        sendAlertEmail(dataType + "同步重试失败", errorMsg + "\n原因：" + (lastException != null ? lastException.getMessage() : "未知"));

        throw new RuntimeException(errorMsg, lastException);
    }

    /**
     * 判断是否为可重试异常
     *
     * @param e 异常
     * @return true-可重试，false-不可重试
     */
    private boolean isRetryableException(Exception e) {
        // 网络/连接相关异常可重试
        if (e instanceof org.springframework.dao.RecoverableDataAccessException) {
            return true;
        }
        if (e instanceof ConnectException || e instanceof SocketTimeoutException) {
            return true;
        }

        // 包含连接/超时/重试关键字的异常可重试
        if (e.getMessage() != null) {
            String msg = e.getMessage().toLowerCase();
            return msg.contains("connection") || msg.contains("timeout") || msg.contains("retry") || msg.contains("meilisearch");
        }

        // 数据格式/参数错误不可重试
        if (e instanceof IllegalArgumentException) {
            return false;
        }

        // 默认可重试
        return true;
    }

    /**
     * 获取Meilisearch索引中文档数量
     *
     * @param indexName 索引名
     * @return 文档数量
     */
    private long getMeiliSearchDocumentCount(String indexName) {
        // 前置判断：检查参数是否为空
        if (indexName == null || indexName.trim().isEmpty()) {
            log.warn("Meilisearch索引名称为空，跳过计数操作");
            return 0;
        }

        // 前置判断：检查meiliSearchClient是否可用
        if (meiliSearchClient == null) {
            log.error("meiliSearchClient未初始化，无法执行计数操作");
            sendAlertEmail("Meilisearch连接异常", "meiliSearchClient未初始化，无法执行计数操作");
            return 0;
        }

        try {
            com.meilisearch.sdk.SearchRequest searchRequest = com.meilisearch.sdk.SearchRequest.builder()
                    .limit(0)
                    .build();
            com.meilisearch.sdk.model.SearchResult searchResult = (com.meilisearch.sdk.model.SearchResult) meiliSearchClient.index(indexName).search(searchRequest);
            long count = searchResult.getEstimatedTotalHits();
            log.debug("获取Meilisearch索引{}文档数量成功：{}", indexName, count);
            return count;
        } catch (Exception e) {
            log.warn("获取Meilisearch索引{}文档数量失败: {}", indexName, e.getMessage(), e);
            sendAlertEmail("Meilisearch索引计数异常", "获取Meilisearch索引[" + indexName + "]文档数量失败：\n" + e.getMessage());
            return 0;
        }
    }

    /**
     * 发送告警邮件（封装频率控制逻辑）
     *
     * @param title 邮件标题
     * @param content 邮件内容
     */
    private void sendAlertEmail(String title, String content) {
        long currentTime = System.currentTimeMillis();

        // 频率控制：避免短时间内发送过多邮件
        if (currentTime - lastAlertTime >= alertInterval) {
            synchronized (this) {
                if (currentTime - lastAlertTime >= alertInterval) {
                    try {
                        emailSenderUtil.sendEsFailureAlert(adminEmail, title, content);
                        log.info("告警邮件发送成功：{}", title);
                        lastAlertTime = currentTime;
                        // 重置该类型告警的失败计数
                        alertFailureCount.computeIfAbsent(title, k -> new AtomicInteger()).set(0);
                    } catch (Exception e) {
                        log.error("发送告警邮件失败: " + e.getMessage(), e);

                        // 增加该类型告警的失败计数
                        String alertType = title;
                        AtomicInteger counter = alertFailureCount.computeIfAbsent(alertType, k -> new AtomicInteger());
                        int currentFailureCount = counter.incrementAndGet();

                        // 只有连续失败达到阈值时才记录错误日志，减少告警噪音
                        if (currentFailureCount >= ALERT_FAILURE_THRESHOLD) {
                            log.error("【终极兜底】告警邮件发送失败，连续失败{}次，原始异常：{} - {}", currentFailureCount, title, content);
                        } else {
                            log.debug("告警邮件发送失败，已累计失败{}次：{}", currentFailureCount, title);
                        }
                    }
                }
            }
        } else {
            log.info("告警邮件发送频率限制，跳过本次发送：{}", title);
            log.debug("【兜底日志】{}：{}", title, content);
        }
    }

    // ===================== QueryWrapper构建器 =====================

    /**
     * 构建图片全量同步查询条件
     *
     * @return 查询条件
     */
    private QueryWrapper<Picture> buildPictureQueryWrapper() {
        return new QueryWrapper<Picture>()
                .eq("isDelete", 0)
                .eq("isDraft", 0)
                .isNull("spaceId");
    }

    /**
     * 构建图片增量同步查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<Picture> buildPictureIncrementalQueryWrapper(Date syncTime) {
        return new QueryWrapper<Picture>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("reviewStatus", 1)
                .eq("isDraft", 0)
                .isNull("spaceId");
    }

    /**
     * 构建图片审核未通过查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<Picture> buildPictureUnreviewedQueryWrapper(Date syncTime) {
        return new QueryWrapper<Picture>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("reviewStatus", 0)
                .eq("isDraft", 0)
                .isNull("spaceId");
    }

    /**
     * 构建图片审核通过查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<Picture> buildPictureReviewedQueryWrapper(Date syncTime) {
        return new QueryWrapper<Picture>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("reviewStatus", 1)
                .eq("isDraft", 0)
                .isNull("spaceId");
    }

    /**
     * 构建用户全量同步查询条件
     *
     * @return 查询条件
     */
    private QueryWrapper<User> buildUserQueryWrapper() {
        return new QueryWrapper<User>()
                .eq("isDelete", 0)
                .ne("userRole", "ban");
    }

    /**
     * 构建用户增量同步查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<User> buildUserIncrementalQueryWrapper(Date syncTime) {
        return new QueryWrapper<User>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .ne("userRole", "ban");
    }

    /**
     * 构建用户被封禁查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<User> buildUserBannedQueryWrapper(Date syncTime) {
        return new QueryWrapper<User>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("userRole", "ban");
    }

    /**
     * 构建帖子全量同步查询条件
     *
     * @return 查询条件
     */
    private QueryWrapper<Post> buildPostQueryWrapper() {
        return new QueryWrapper<Post>()
                .eq("isDelete", 0)
                .eq("status", 1)
                .eq("isDraft", 0); // 只同步非草稿的帖子
    }

    /**
     * 构建帖子增量同步查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<Post> buildPostIncrementalQueryWrapper(Date syncTime) {
        return new QueryWrapper<Post>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("status", 1)
                .eq("isDraft", 0); // 只同步非草稿的帖子
    }

    /**
     * 构建帖子审核未通过查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<Post> buildPostUnreviewedQueryWrapper(Date syncTime) {
        return new QueryWrapper<Post>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("status", 0)
                .eq("isDraft", 0);  // 只考虑非草稿的帖子
    }

    /**
     * 构建空间全量同步查询条件
     *
     * @return 查询条件
     */
    private QueryWrapper<Space> buildSpaceQueryWrapper() {
        return new QueryWrapper<Space>()
                .eq("isDelete", 0)
                .eq("spaceType", 1);
    }

    /**
     * 构建空间增量同步查询条件
     *
     * @param syncTime 同步时间阈值
     * @return 查询条件
     */
    private QueryWrapper<Space> buildSpaceIncrementalQueryWrapper(Date syncTime) {
        return new QueryWrapper<Space>()
                .ge("updateTime", syncTime)
                .eq("isDelete", 0)
                .eq("spaceType", 1);
    }

    // ===================== 文档构建器 =====================

    private Object buildPictureIndexQuery(Picture picture) {
        return picture;
    }

    private Object buildUserIndexQuery(User user) {
        return user;
    }

    private Object buildPostIndexQuery(Post post) {
        // Calculate hot score physically before syncing
        long viewCount = post.getViewCount() != null ? post.getViewCount() : 0L;
        long likeCount = post.getLikeCount() != null ? post.getLikeCount() : 0L;
        long commentCount = post.getCommentCount() != null ? post.getCommentCount() : 0L;
        long shareCount = post.getShareCount() != null ? post.getShareCount() : 0L;
        double hotScore = likeCount * 0.3 + commentCount * 0.25 + viewCount * 0.2 + shareCount * 0.15;
        post.setHotScore(hotScore);
        return post;
    }

    private Object buildSpaceIndexQuery(Space space) {
        return space;
    }

    // ===================== 索引管理方法 =====================

    /**
     * 删除并重建Meilisearch索引以确保最新字段映射
     */
    private void deleteAndRecreateIndices() {
        try {
            // 删除现有索引
            String[] indices = {INDEX_NAME_PICTURE, INDEX_NAME_USER, INDEX_NAME_POST, INDEX_NAME_SPACE};
            for (String indexName : indices) {
                try {
                    meiliSearchClient.deleteIndex(indexName);
                    log.info("已删除Meilisearch {}索引", indexName);
                } catch (Exception e) {
                    log.info("Meilisearch {}索引不存在，无需删除", indexName);
                }
            }

            // 短暂等待
            TimeUnit.MILLISECONDS.sleep(500);

            // 初始化所有索引
            initIndices();
            log.info("Meilisearch索引初始化完成");
        } catch (Exception e) {
            log.error("删除/初始化Meilisearch索引失败: " + e.getMessage(), e);
            sendAlertEmail("Meilisearch索引初始化异常", "初始化Meilisearch索引失败：\n" + e.getMessage());
        }
    }

    /**
     * 初始化 Meilisearch 索引结构和配置
     */
    private void initIndices() {
        initIndex(INDEX_NAME_PICTURE, new String[]{"name", "introduction", "category", "tags"},
                new String[]{"userId", "spaceId", "category", "tags", "reviewStatus", "isDelete", "isDraft"},
                new String[]{"createTime", "updateTime", "viewCount", "shareCount", "likeCount", "hotScore"});

        initIndex(INDEX_NAME_USER, new String[]{"userName", "userAccount", "userProfile"},
                new String[]{"userRole", "isDelete"},
                new String[]{"createTime", "updateTime"});

        initIndex(INDEX_NAME_POST, new String[]{"title", "content", "category", "tags"},
                new String[]{"userId", "category", "tags", "status", "isDelete"},
                new String[]{"createTime", "updateTime", "hotScore"});

        initIndex(INDEX_NAME_SPACE, new String[]{"spaceName", "spaceDesc"},
                new String[]{"userId", "spaceType", "spaceLevel", "isDelete"},
                new String[]{"createTime", "updateTime"});

        initIndex("search_keyword", new String[]{"keyword"},
                new String[]{"type", "keyword"},
                new String[]{"count", "updateTime"});

        initIndex("rag_memory", new String[]{"content"},
                new String[]{"userId", "sessionId", "memoryType", "summaryLevel"},
                new String[]{"createTime"});
    }

    /**
     * 单个索引的创建与属性配置
     */
    private void initIndex(String indexName, String[] searchable, String[] filterable, String[] sortable) {
        try {
            log.info("检查Meilisearch索引配置: {}", indexName);
            try {
                meiliSearchClient.getIndex(indexName);
            } catch (Exception e) {
                log.info("创建Meilisearch索引: {}", indexName);
                meiliSearchClient.createIndex(indexName, "id");
                TimeUnit.MILLISECONDS.sleep(500);
            }

            Index index = meiliSearchClient.index(indexName);
            Settings settings = new Settings();
            settings.setSearchableAttributes(searchable);
            settings.setFilterableAttributes(filterable);
            settings.setSortableAttributes(sortable);
            index.updateSettings(settings);
            log.info("成功更新Meilisearch索引{}配置", indexName);
        } catch (Exception e) {
            log.error("初始化Meilisearch索引{}失败", indexName, e);
        }
    }

    // ===================== 反射工具方法（提取ID） =====================

    /**
     * 提取实体ID（通用方法）
     *
     * @param entity 实体对象
     * @return ID字符串
     */
    private String extractId(Object entity) {
        try {
            return entity.getClass().getMethod("getId").invoke(entity).toString();
        } catch (Exception e) {
            log.error("提取实体ID失败", e);
            return "未知ID";
        }
    }

    /**
     * 提取实体列表ID（通用方法）
     *
     * @param entityList 实体列表
     * @return ID列表
     */
    private <T> List<String> extractIds(List<T> entityList) {
        return entityList.stream()
                .map(this::extractId)
                .collect(Collectors.toList());
    }

    /**
     * 将实体转换为符合Meilisearch的Map列表结构（转换为ID为String、移除null值）
     */
    private List<Map<String, Object>> convertToMeiliDocs(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object obj : list) {
            try {
                Map<String, Object> map = JSONUtil.parseObj(obj);
                if (map.containsKey("id") && map.get("id") != null) {
                    map.put("id", map.get("id").toString());
                }
                // 过滤null值，确保Meilisearch的 NOT EXISTS 逻辑正常运行
                map.entrySet().removeIf(entry -> entry.getValue() == null);
                result.add(map);
            } catch (Exception e) {
                log.error("转换对象为Meilisearch文档失败", e);
            }
        }
        return result;
    }

    /**
     * 通用批量处理方法，按批次大小拆分处理并添加批次间休眠
     *
     * @param dataList 待处理数据列表
     * @param batchSize 批次大小
     * @param processor 单批次处理器
     * @param <T> 数据类型
     */
    private <T> void processInBatches(List<T> dataList, int batchSize, java.util.function.Consumer<List<T>> processor) {
        if (dataList == null || dataList.isEmpty()) {
            return;
        }

        for (int i = 0; i < dataList.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, dataList.size());
            List<T> batch = dataList.subList(i, endIndex);

            try {
                processor.accept(batch);

                // 批次间短暂休眠，降低Meilisearch瞬时压力
                if (i + batchSize < dataList.size()) {
                    Thread.sleep(BATCH_SLEEP_TIME);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("批量处理被中断", e);
                break;
            } catch (Exception e) {
                log.error("处理批次数据失败", e);
                // 继续处理下一个批次
            }
        }
    }

    /**
     * Spring容器销毁钩子，优雅关闭线程池
     */
    @PreDestroy
    public void destroy() {
        log.info("MeiliSearchSyncJob正在关闭，开始清理资源...");

        // 优雅关闭线程池
        if (syncExecutor != null && !syncExecutor.isShutdown()) {
            try {
                // 不接受新任务，等待现有任务完成
                syncExecutor.shutdown();

                // 等待最多30秒让任务完成
                if (!syncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("线程池任务未在30秒内完成，强制关闭");
                    syncExecutor.shutdownNow();

                    // 再等待一段时间让线程真正结束
                    if (!syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("线程池未能正常关闭");
                    }
                }
            } catch (InterruptedException e) {
                log.error("等待线程池关闭时被中断", e);
                syncExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("MeiliSearchSyncJob资源清理完成");
    }
}
