package com.lumenglover.yuemupicturebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.constant.UserConstant;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.manager.CrawlerManager;
import com.lumenglover.yuemupicturebackend.manager.RecommendationManager;
import com.lumenglover.yuemupicturebackend.mapper.PostMapper;
import com.lumenglover.yuemupicturebackend.model.dto.post.PostAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.post.PostQueryRequest;
import com.lumenglover.yuemupicturebackend.model.dto.viewrecord.ViewRecordAddRequest;
import com.lumenglover.yuemupicturebackend.model.entity.*;
import com.lumenglover.yuemupicturebackend.model.vo.PostTagCategory;
import com.lumenglover.yuemupicturebackend.model.vo.PostVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.*;
import com.lumenglover.yuemupicturebackend.utils.EmailSenderUtil;
import com.lumenglover.yuemupicturebackend.utils.PostScoreUpdateTracker;
import com.lumenglover.yuemupicturebackend.utils.SensitiveUtil;
import com.lumenglover.yuemupicturebackend.utils.SystemNotifyUtil;
import com.lumenglover.yuemupicturebackend.config.CosClientConfig;
import com.lumenglover.yuemupicturebackend.config.PexelsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PostServiceImpl extends ServiceImpl<PostMapper, Post> implements PostService {

    @Resource
    private UserService userService;

    @Resource
    private UserFollowsService userfollowsService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private LikeRecordService likeRecordService;

    @Resource
    @Lazy
    private ShareRecordService shareRecordService;

    @Resource
    private CrawlerManager crawlerManager;

    @Resource
    private SystemNotifyUtil systemNotifyUtil;

    @Resource
    private SensitiveUtil sensitiveUtil;

    @Resource
    private EmailSenderUtil emailSenderUtil;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private ViewRecordService viewRecordService;

    @Resource
    @Lazy
    private FavoriteRecordService favoriteRecordService;

    @Resource
    private TagService tagService;

    @Resource
    private CategoryService categoryService;

    @Resource
    private RecommendationManager recommendationManager;

    @Resource
    private PostScoreUpdateTracker postScoreUpdateTracker;

    @Resource
    @Lazy
    private CommentsService commentsService;

    @Resource
    @Lazy
    private PexelsConfig pexelsConfig;

    @Resource
    private com.lumenglover.yuemupicturebackend.config.RagConfig ragConfig;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPost(PostAddRequest postAddRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(postAddRequest == null, ErrorCode.PARAMS_ERROR);

        String title = postAddRequest.getTitle();
        String content = postAddRequest.getContent();

        // 如果请求中包含ID，则执行更新操作（用于发布草稿）
        if (postAddRequest.getId() != null) {
            // 获取原帖子信息
            Post oldPost = this.getById(postAddRequest.getId());
            ThrowUtils.throwIf(oldPost == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

            // 校验权限 - 只有本人或管理员可以更新
            if (!oldPost.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // 对帖子内容进行敏感词过滤
            String filteredTitle = SensitiveUtil.filter(title);
            String filteredContent = SensitiveUtil.filter(content);

            // 检查是否包含敏感词
            boolean hasSensitiveWords = !Objects.equals(title, filteredTitle) ||
                    !Objects.equals(content, filteredContent);

            // 更新帖子信息
            Post updatePost = new Post();
            updatePost.setId(postAddRequest.getId());
            updatePost.setTitle(postAddRequest.getTitle());
            updatePost.setContent(postAddRequest.getContent());
            updatePost.setCategory(postAddRequest.getCategory());

            // 设置封面图，直接使用前端传递的封面图URL
            updatePost.setCoverUrl(postAddRequest.getCoverUrl());

            // 设置标签，直接使用前端传递的标签
            if (postAddRequest.getTags() != null) {
                updatePost.setTags(cn.hutool.json.JSONUtil.toJsonStr(postAddRequest.getTags()));
            } else {
                updatePost.setTags(null);
            }
            // AI 审核（超时降级为敏感词兜底）
            boolean needHumanReviewUpdate = false;
            String reviewReasonUpdate = "";
            try {
                String endpoint = ragConfig.getPythonService().getBaseUrl()
                        + ragConfig.getPythonService().getAi().getPostModerationEndpoint();
                java.util.Map<String, Object> req = new java.util.HashMap<>();
                req.put("title", title);
                req.put("content", content);
                log.info("[帖子审核-更新] 调用AI审核: {}", endpoint);
                String resp = cn.hutool.http.HttpUtil.post(endpoint, cn.hutool.json.JSONUtil.toJsonStr(req), 8000);
                cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(resp);
                if (obj.getInt("code", -1) == 200) {
                    cn.hutool.json.JSONObject data = obj.getJSONObject("data");
                    boolean safe = data.getBool("safe", true);
                    reviewReasonUpdate = data.getStr("reason", "");
                    if (!safe) { needHumanReviewUpdate = true; log.warn("[帖子审核-更新] AI判违规: {}", reviewReasonUpdate); }
                    else { log.info("[帖子审核-更新] AI通过: {}", reviewReasonUpdate); }
                }
            } catch (Exception aiEx) {
                log.warn("[帖子审核-更新] AI超时，降级敏感词兜底: {}", aiEx.getMessage());
                needHumanReviewUpdate = hasSensitiveWords;
                reviewReasonUpdate = hasSensitiveWords ? "敏感词兜底检测到违规" : "";
            }

            updatePost.setStatus(needHumanReviewUpdate ? 0 : 1);
            updatePost.setIsDelete(0);
            updatePost.setIsDraft(0);

            boolean success = this.updateById(updatePost);
            ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR);

            if (needHumanReviewUpdate) {
                try {
                    String adminEmail = cosClientConfig.getAdminEmail();
                    if (StrUtil.isNotBlank(adminEmail)) {
                        emailSenderUtil.sendReviewEmail(adminEmail, String.format(
                                "更新帖子需人工审核。\n标题：%s\n原因：%s\n帖子ID：%s",
                                updatePost.getTitle(), reviewReasonUpdate, updatePost.getId()));
                    }
                } catch (Exception e) { log.error("发送更新审核邮件失败: {}", e.getMessage()); }
            } else {
                SystemNotifyUtil.sendPostApprovedNotify(loginUser.getId(), updatePost.getId(), updatePost.getTitle());
                generateSummaryCommentIfApproved(updatePost.getId());
                log.info("[帖子审核-更新] 通过，帖子ID: {}", updatePost.getId());
            }

            return updatePost.getId();
        }

        // 标题校验
        ThrowUtils.throwIf(StrUtil.isBlank(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(title.length() > 100, ErrorCode.PARAMS_ERROR, "标题最多100字");
        ThrowUtils.throwIf(StrUtil.isBlank(content), ErrorCode.PARAMS_ERROR, "内容不能为空");

        // 创建帖子对象
        Post post = new Post();
        post.setTitle(postAddRequest.getTitle());
        post.setContent(postAddRequest.getContent());
        post.setCategory(postAddRequest.getCategory());
        post.setUserId(loginUser.getId());
        post.setCoverUrl(postAddRequest.getCoverUrl());
        post.setTags(postAddRequest.getTags() != null ? cn.hutool.json.JSONUtil.toJsonStr(postAddRequest.getTags()) : null);
        post.setIsDelete(0);
        post.setIsDraft(0);

        // AI 内容审核（超时则降级为敏感词工具兜底）
        boolean needHumanReview = false;
        String reviewReason = "";
        try {
            String endpoint = ragConfig.getPythonService().getBaseUrl()
                    + ragConfig.getPythonService().getAi().getPostModerationEndpoint();
            java.util.Map<String, Object> req = new java.util.HashMap<>();
            req.put("title", title);
            req.put("content", content);
            log.info("[帖子审核] 调用AI审核: {}", endpoint);
            String resp = cn.hutool.http.HttpUtil.post(endpoint, cn.hutool.json.JSONUtil.toJsonStr(req), 8000);
            cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(resp);
            if (obj.getInt("code", -1) == 200) {
                cn.hutool.json.JSONObject data = obj.getJSONObject("data");
                boolean safe = data.getBool("safe", true);
                reviewReason = data.getStr("reason", "");
                if (!safe) { needHumanReview = true; log.warn("[帖子审核] AI判违规: {}", reviewReason); }
                else { log.info("[帖子审核] AI通过: {}", reviewReason); }
            }
        } catch (Exception aiEx) {
            log.warn("[帖子审核] AI超时，降级敏感词兜底: {}", aiEx.getMessage());
            String ft = SensitiveUtil.filter(title), fc = SensitiveUtil.filter(content);
            needHumanReview = !Objects.equals(title, ft) || !Objects.equals(content, fc);
            reviewReason = needHumanReview ? "敏感词兜底检测到违规" : "";
        }

        post.setStatus(needHumanReview ? 0 : 1);
        boolean success = this.save(post);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR);
        log.info("帖子保存完成，ID: {}, status: {}", post.getId(), post.getStatus());

        if (needHumanReview) {
            try {
                String adminEmail = cosClientConfig.getAdminEmail();
                if (StrUtil.isNotBlank(adminEmail)) {
                    emailSenderUtil.sendReviewEmail(adminEmail, String.format(
                            "帖子需人工审核。\n标题：%s\n发布者：%s\n原因：%s\n帖子ID：%s",
                            post.getTitle(), loginUser.getUserName(), reviewReason, post.getId()));
                }
            } catch (Exception e) { log.error("发送审核邮件失败: {}", e.getMessage()); }
        } else {
            SystemNotifyUtil.sendPostApprovedNotify(loginUser.getId(), post.getId(), post.getTitle());
            generateSummaryCommentIfApproved(post.getId());
        }

        log.info("帖子创建成功，ID: {}，标签: {}", post.getId(), post.getTags());
        return post.getId();
    }


    @Override
    public Map<String, Object> getPostStatusStats() {
        // 统计帖子状态信息
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("isDelete", 0); // 未删除的帖子

        // 总帖子数
        long total = this.count(queryWrapper);

        // 已审核通过的帖子数
        long approved = this.count(queryWrapper.clone().eq("status", 1));

        // 待审核的帖子数
        long pending = this.count(queryWrapper.clone().eq("status", 0));

        // 审核不通过的帖子数
        long rejected = this.count(queryWrapper.clone().eq("status", 2));

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("approved", approved);
        stats.put("pending", pending);
        stats.put("rejected", rejected);

        return stats;
    }

    /**
     * 构建敏感词通知邮件内容
     */
    private String buildSensitiveWordNotificationContent(Post post, User loginUser, String filteredTitle,
                                                         String filteredContent) {
        StringBuilder content = new StringBuilder();
        content.append("<h2>帖子敏感词审核通知</h2>");
        content.append("<p><strong>帖子ID:</strong> ").append(post.getId()).append("</p>");
        content.append("<p><strong>上传用户ID:</strong> ").append(loginUser.getId()).append("</p>");
        content.append("<p><strong>上传用户名:</strong> ").append(loginUser.getUserName()).append("</p>");
        content.append("<p><strong>上传用户邮箱:</strong> ").append(loginUser.getEmail()).append("</p>");
        content.append("<p><strong>帖子标题:</strong> ").append(post.getTitle() != null ? post.getTitle() : "无")
                .append("</p>");
        content.append("<p><strong>帖子内容:</strong> ").append(post.getContent() != null ? post.getContent() : "无")
                .append("</p>");
        content.append("<p><strong>过滤后标题:</strong> ").append(filteredTitle).append("</p>");
        content.append("<p><strong>过滤后内容:</strong> ").append(filteredContent).append("</p>");
        content.append("<p><strong>上传时间:</strong> ").append(post.getCreateTime()).append("</p>");
        content.append("<p>请登录系统进行人工审核。</p>");

        return content.toString();
    }

    /**
     * 构建更新帖子敏感词通知邮件内容
     */
    private String buildSensitiveWordNotificationContentForUpdate(Post post, String filteredTitle,
                                                                  String filteredContent) {
        StringBuilder content = new StringBuilder();
        content.append("<h2>更新帖子敏感词审核通知</h2>");
        content.append("<p><strong>帖子ID:</strong> ").append(post.getId()).append("</p>");
        content.append("<p><strong>帖子标题:</strong> ").append(post.getTitle() != null ? post.getTitle() : "无")
                .append("</p>");
        content.append("<p><strong>帖子内容:</strong> ").append(post.getContent() != null ? post.getContent() : "无")
                .append("</p>");
        content.append("<p><strong>过滤后标题:</strong> ").append(filteredTitle).append("</p>");
        content.append("<p><strong>过滤后内容:</strong> ").append(filteredContent).append("</p>");
        content.append("<p><strong>更新时间:</strong> ").append(post.getUpdateTime()).append("</p>");
        content.append("<p>请登录系统进行人工审核。</p>");

        return content.toString();
    }

    /**
     * 检测爬虫或恶意请求
     */
    private void crawlerDetect(HttpServletRequest request) {
        crawlerManager.detectNormalRequest(request);
    }

    @Override
    public PostVO getPostDetail(Long id, User loginUser, HttpServletRequest request) {
        // 参数校验
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);

        // 检测爬虫
        crawlerDetect(request);

        // 【修正点1】先获取Post实体，再转VO
        Post postEntity = this.getById(id);
        ThrowUtils.throwIf(postEntity == null, ErrorCode.NOT_FOUND_ERROR);
        PostVO post = PostVO.objToVo(postEntity);

        // 增加浏览量
        incrementViewCount(id, request);

        String content = post.getContent();
        post.setContent(content);

        // 填充用户信息
        User user = userService.getById(post.getUserId());
        post.setUser(userService.getUserVO(user));

        // 设置点赞和分享状态
        if (loginUser != null) {
            boolean isLiked = likeRecordService.isContentLiked(post.getId(), 2, loginUser.getId());
            post.setIsLiked(isLiked ? 1 : 0);
            boolean isShared = shareRecordService.isContentShared(post.getId(), 2, loginUser.getId());
            post.setIsShared(isShared ? 1 : 0);

            // 获取收藏状态
            boolean isFavorited = favoriteRecordService.hasFavorited(loginUser.getId(), post.getId(), 2); // 2表示帖子类型
            post.setIsFavorited(isFavorited ? 1 : 0);

            // 自动添加浏览记录
            try {
                addPostViewRecord(post.getId(), loginUser.getId(), request);
            } catch (Exception e) {
                log.error("添加帖子浏览记录失败", e);
            }
        } else {
            post.setIsLiked(0);
            post.setIsShared(0);
            post.setIsFavorited(0);
        }

        // 获取最新的浏览量
        long realViewCount = getViewCount(id);
        post.setViewCount(realViewCount);

        return post;
    }

    @Override
    public Page<PostVO> listPosts(PostQueryRequest postQueryRequest, User loginUser) {
        long current = postQueryRequest.getCurrent();
        long size = postQueryRequest.getPageSize();

        // 构建查询条件
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();

        // 搜索词
        String searchText = postQueryRequest.getSearchText();
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.like("title", searchText).or().like("content", searchText);
        }

        // 分类
        String category = postQueryRequest.getCategory();
        if (StrUtil.isNotBlank(category)) {
            queryWrapper.eq("category", category);
        }

        // 用户ID
        Long userId = postQueryRequest.getUserId();
        if (userId != null && userId > 0) {
            queryWrapper.eq("userId", userId);
        }

        // 处理查询范围
        boolean isPublic = postQueryRequest.getIsPublic();
        boolean isAdmin = loginUser != null && UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());

        if (isPublic || !isAdmin) {
            // 公共查询或非管理员，只显示已发布的帖子
            queryWrapper.eq("status", 1);
        } else {
            // 管理员查询所有状态的帖子
            Integer status = postQueryRequest.getStatus();
            if (status != null) {
                queryWrapper.eq("status", status);
            }
        }

        queryWrapper.eq("isDelete", 0);
        queryWrapper.eq("isDraft", 0); // 只查询非草稿的帖子
        queryWrapper.orderByDesc("createTime");

        // 执行查询
        Page<Post> postPage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为VO并填充信息
        Page<PostVO> postVOPage = new Page<>();
        BeanUtils.copyProperties(postPage, postVOPage);
        // 【修正点2】正确转换Post实体到PostVO
        List<PostVO> postVOList = postPage.getRecords().stream().map(PostVO::objToVo).collect(Collectors.toList());

        // 填充帖子信息
        fillPostsInfo(postVOList, loginUser);

        postVOPage.setRecords(postVOList);
        return postVOPage;
    }

    /**
     * 批量填充帖子信息
     */
    private void fillPostsInfo(List<PostVO> posts, User loginUser) {
        if (CollUtil.isEmpty(posts)) {
            return;
        }

        // 获取所有帖子ID
        Set<Long> postIds = posts.stream().map(PostVO::getId).collect(Collectors.toSet());

        // 批量查询用户信息
        Map<Long, User> userMap = getUserMapFromVO(posts);

        // 获取登录用户的点赞、分享和收藏信息
        Map<Long, Boolean> likeMap = new HashMap<>();
        Map<Long, Boolean> shareMap = new HashMap<>();
        Map<Long, Boolean> favoriteMap = new HashMap<>();
        if (loginUser != null) {
            likeMap = getPostIdIsLikedMap(loginUser, postIds);
            shareMap = getPostIdIsSharedMap(loginUser, postIds);
            favoriteMap = getPostIdIsFavoritedMap(loginUser, postIds);
        }

        // 批量获取浏览量 - 优化：使用 PostVO 中已有的 viewCount 作为基础值
        Map<Long, Long> viewCountMap = new HashMap<>();
        List<String> viewCountKeys = postIds.stream()
                .map(postId -> String.format("post:viewCount:%d", postId))
                .collect(Collectors.toList());

        if (!viewCountKeys.isEmpty()) {
            // 先构建 PostVO 的 ID -> viewCount 映射
            Map<Long, Long> baseViewCountMap = posts.stream()
                    .collect(Collectors.toMap(
                            PostVO::getId,
                            post -> post.getViewCount() != null ? post.getViewCount() : 0L));

            // 批量从 Redis 获取增量
            List<String> redisViewCounts = stringRedisTemplate.opsForValue().multiGet(viewCountKeys);
            int i = 0;
            for (Long postId : postIds) {
                String redisCount = redisViewCounts != null && i < redisViewCounts.size() ? redisViewCounts.get(i)
                        : null;
                long baseCount = baseViewCountMap.getOrDefault(postId, 0L);
                long increment = redisCount != null ? Long.parseLong(redisCount) : 0L;
                viewCountMap.put(postId, baseCount + increment);
                i++;
            }
        }

        // 填充信息
        for (PostVO post : posts) {
            // 清空内容，只在详情页显示
            post.setContent(null);
            // 设置用户信息
            User user = userMap.get(post.getUserId());
            if (user != null) {
                post.setUser(userService.getUserVO(user));
            }
            // 设置点赞、分享和收藏状态
            post.setIsLiked(likeMap.getOrDefault(post.getId(), false) ? 1 : 0);
            post.setIsShared(shareMap.getOrDefault(post.getId(), false) ? 1 : 0);
            post.setIsFavorited(favoriteMap.getOrDefault(post.getId(), false) ? 1 : 0);
            // 设置实时浏览量
            post.setViewCount(
                    viewCountMap.getOrDefault(post.getId(), post.getViewCount() != null ? post.getViewCount() : 0L));
        }
    }

    /**
     * 从VO列表获取用户信息映射
     */
    private Map<Long, User> getUserMapFromVO(List<PostVO> posts) {
        Set<Long> userIds = posts.stream().map(PostVO::getUserId).collect(Collectors.toSet());
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    /**
     * 填充用户点赞状态
     */
    private void fillUserLikeStatus(List<PostVO> posts, Long userId) {
        if (CollUtil.isEmpty(posts) || userId == null) {
            return;
        }
        Set<Long> postIds = posts.stream().map(PostVO::getId).collect(Collectors.toSet());
        Map<Long, Boolean> likeMap = getPostIdIsLikedMap(userService.getById(userId), postIds);
        posts.forEach(post -> post.setIsLiked(likeMap.getOrDefault(post.getId(), false) ? 1 : 0));
    }

    /**
     * 获取帖子的点赞状态映射
     */
    private Map<Long, Boolean> getPostIdIsLikedMap(User currentUser, Set<Long> postIds) {
        // 使用通用点赞表查询
        QueryWrapper<LikeRecord> likeQueryWrapper = new QueryWrapper<>();
        likeQueryWrapper.in("targetId", postIds)
                .eq("userId", currentUser.getId())
                .eq("targetType", 2) // 2表示帖子类型
                .eq("isLiked", true);

        List<LikeRecord> likeRecords = likeRecordService.list(likeQueryWrapper);

        return likeRecords.stream()
                .collect(Collectors.toMap(
                        LikeRecord::getTargetId,
                        like -> true,
                        (b1, b2) -> b1));
    }

    /**
     * 获取帖子的分享状态映射
     */
    private Map<Long, Boolean> getPostIdIsSharedMap(User currentUser, Set<Long> postIds) {
        // 查询分享记录
        QueryWrapper<ShareRecord> shareQueryWrapper = new QueryWrapper<>();
        shareQueryWrapper.in("targetId", postIds)
                .eq("userId", currentUser.getId())
                .eq("targetType", 2) // 2表示帖子类型
                .eq("isShared", true);

        List<ShareRecord> shareRecords = shareRecordService.list(shareQueryWrapper);

        return shareRecords.stream()
                .collect(Collectors.toMap(
                        ShareRecord::getTargetId,
                        share -> true,
                        (b1, b2) -> b1));
    }

    /**
     * 获取帖子的收藏状态映射
     */
    private Map<Long, Boolean> getPostIdIsFavoritedMap(User currentUser, Set<Long> postIds) {
        // 使用通用收藏表查询
        QueryWrapper<FavoriteRecord> favoriteQueryWrapper = new QueryWrapper<>();
        favoriteQueryWrapper.in("targetId", postIds)
                .eq("userId", currentUser.getId())
                .eq("targetType", 2) // 2表示帖子类型
                .eq("isFavorite", true);

        List<FavoriteRecord> favoriteRecords = favoriteRecordService.list(favoriteQueryWrapper);

        return favoriteRecords.stream()
                .collect(Collectors.toMap(
                        FavoriteRecord::getTargetId,
                        favorite -> true,
                        (b1, b2) -> b1));
    }

    /**
     * 获取用户信息映射
     */
    private Map<Long, User> getUserMap(List<Post> posts) {
        Set<Long> userIds = posts.stream().map(Post::getUserId).collect(Collectors.toSet());
        return userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewPost(Long postId, Integer status, String message, User loginUser) {
        // 参数校验
        Post post = this.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 校验权限
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 校验帖子是否为草稿，草稿不能审核
        if (post.getIsDraft() != null && post.getIsDraft() == 1) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿不能进行审核");
        }

        // 更新审核状态
        Post updatePost = new Post();
        updatePost.setId(postId);

        // 管理员审核具有最高优先级，直接采用管理员的审核决定
        updatePost.setStatus(status);
        updatePost.setReviewMessage(message);

        boolean success = this.updateById(updatePost);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR);

        // 重新获取post对象，确保获取到最新的信息
        post = this.getById(postId);

        // 发送系统通知
        if (status == 1) {
            // 审核通过通知
            SystemNotifyUtil.sendPostApprovedNotify(post.getUserId(), postId, post.getTitle());
            generateSummaryCommentIfApproved(postId);
        } else if (status == 2) {
            // 审核不通过通知
            SystemNotifyUtil.sendPostRejectedNotify(post.getUserId(), postId, post.getTitle(), message);
        }
    }

    @Override
    public Page<PostVO> listMyPosts(PostQueryRequest request) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();

        // 必须是当前用户的帖子
        ThrowUtils.throwIf(request.getUserId() == null, ErrorCode.PARAMS_ERROR);
        queryWrapper.eq("userId", request.getUserId());

        // 构建查询条件
        if (StrUtil.isNotBlank(request.getCategory())) {
            queryWrapper.eq("category", request.getCategory());
        }

        // 处理审核状态查询
        if (request.getStatus() != null) {
            queryWrapper.eq("status", request.getStatus());
        }

        // 搜索标题和内容
        if (StrUtil.isNotBlank(request.getSearchText())) {
            queryWrapper.and(wrap -> wrap
                    .like("title", request.getSearchText())
                    .or()
                    .like("content", request.getSearchText()));
        }

        queryWrapper.orderByDesc("createTime");

        // 分页查询
        Page<Post> postPage = this.page(new Page<>(request.getCurrent(), request.getPageSize()), queryWrapper);

        // 转换为VO并填充信息
        Page<PostVO> postVOPage = new Page<>();
        BeanUtils.copyProperties(postPage, postVOPage);
        // 【修正点3】正确转换Post实体到PostVO
        List<PostVO> postVOList = postPage.getRecords().stream().map(PostVO::objToVo).collect(Collectors.toList());

        // 填充用户信息
        if (CollUtil.isNotEmpty(postVOList)) {
            // 获取用户信息
            User user = userService.getById(request.getUserId());

            // 填充信息
            postVOList.forEach(post -> {
                // 清空内容，只在详情页显示
                post.setContent(null);
                // 设置用户信息
                if (user != null) {
                    post.setUser(userService.getUserVO(user));
                }
                // 设置点赞状态为0（因为是自己的帖子，不需要显示点赞状态）
                post.setIsLiked(0);
                // 设置收藏状态为0（因为是自己的帖子，不需要显示收藏状态）
                post.setIsFavorited(0);
            });
        }

        postVOPage.setRecords(postVOList);
        return postVOPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePost(Post post) {
        // 参数校验
        ThrowUtils.throwIf(post == null || post.getId() == null, ErrorCode.PARAMS_ERROR);

        // 获取原帖子信息
        Post oldPost = this.getById(post.getId());
        ThrowUtils.throwIf(oldPost == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        // 保持不变的字段
        post.setUserId(oldPost.getUserId());
        post.setCreateTime(oldPost.getCreateTime());
        post.setLikeCount(oldPost.getLikeCount());
        post.setCommentCount(oldPost.getCommentCount());
        post.setViewCount(oldPost.getViewCount());
        post.setIsDelete(oldPost.getIsDelete()); // 保持删除状态不变
        // 注意：这里不保留旧的标签，因为标签可能已被更新

        // 对帖子内容进行敏感词过滤
        String filteredTitle = SensitiveUtil.filter(post.getTitle());
        String filteredContent = SensitiveUtil.filter(post.getContent());

        // 检查是否包含敏感词
        boolean hasSensitiveWords = !Objects.equals(post.getTitle(), filteredTitle) ||
                !Objects.equals(post.getContent(), filteredContent);

        // AI 内容审核（超时降级为敏感词工具兜底）
        boolean needHumanReview = false;
        String reviewReason = "";
        try {
            String endpoint = ragConfig.getPythonService().getBaseUrl()
                    + ragConfig.getPythonService().getAi().getPostModerationEndpoint();
            java.util.Map<String, Object> req = new java.util.HashMap<>();
            req.put("title", post.getTitle());
            req.put("content", post.getContent());
            log.info("[帖子审核-edit] 调用AI审核: {}", endpoint);
            String resp = cn.hutool.http.HttpUtil.post(endpoint, cn.hutool.json.JSONUtil.toJsonStr(req), 8000);
            cn.hutool.json.JSONObject obj = cn.hutool.json.JSONUtil.parseObj(resp);
            if (obj.getInt("code", -1) == 200) {
                cn.hutool.json.JSONObject data = obj.getJSONObject("data");
                boolean safe = data.getBool("safe", true);
                reviewReason = data.getStr("reason", "");
                if (!safe) { needHumanReview = true; log.warn("[帖子审核-edit] AI判违规: {}", reviewReason); }
                else { log.info("[帖子审核-edit] AI通过: {}", reviewReason); }
            }
        } catch (Exception aiEx) {
            log.warn("[帖子审核-edit] AI超时，降级敏感词兜底: {}", aiEx.getMessage());
            needHumanReview = hasSensitiveWords;
            reviewReason = hasSensitiveWords ? "敏感词兜底检测到违规" : "";
        }

        post.setStatus(needHumanReview ? 0 : 1);
        post.setUpdateTime(new Date());
        post.setIsDraft(0);
        log.info("更新帖子封面图: {}", post.getCoverUrl());

        boolean success = this.updateById(post);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR, "帖子更新失败");

        if (needHumanReview) {
            try {
                String adminEmail = cosClientConfig.getAdminEmail();
                if (StrUtil.isNotBlank(adminEmail)) {
                    emailSenderUtil.sendReviewEmail(adminEmail, String.format(
                            "编辑帖子需人工审核。\n标题：%s\n原因：%s\n帖子ID：%s",
                            post.getTitle(), reviewReason, post.getId()));
                }
            } catch (Exception e) { log.error("发送编辑审核邮件失败: {}", e.getMessage()); }
        } else {
            SystemNotifyUtil.sendPostApprovedNotify(post.getUserId(), post.getId(), post.getTitle());
            generateSummaryCommentIfApproved(post.getId());
            log.info("[帖子审核-edit] 通过，帖子ID: {}", post.getId());
        }

        return true;
    }


    @Override
    public Page<PostVO> getFollowPosts(HttpServletRequest request, PostQueryRequest postQueryRequest) {
        User loginUser = userService.getLoginUser(request);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 获取当前用户关注的用户ID列表
        QueryWrapper<Userfollows> followsQueryWrapper = new QueryWrapper<>();
        followsQueryWrapper.eq("followerId", loginUser.getId())
                .eq("followStatus", 1);
        List<Userfollows> userFollowsList = userfollowsService.list(followsQueryWrapper);

        if (CollUtil.isEmpty(userFollowsList)) {
            return new Page<>();
        }

        // 提取关注用户的ID
        List<Long> followingIds = userFollowsList.stream()
                .map(Userfollows::getFollowingId)
                .collect(Collectors.toList());

        // 构建查询条件
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("userId", followingIds)
                .eq("status", 1) // 只查询已发布的帖子
                .eq("isDelete", 0);

        // 添加搜索条件
        if (StrUtil.isNotBlank(postQueryRequest.getSearchText())) {
            queryWrapper.and(qw -> qw.like("title", postQueryRequest.getSearchText())
                    .or()
                    .like("content", postQueryRequest.getSearchText()));
        }

        // 添加分类条件
        if (StrUtil.isNotBlank(postQueryRequest.getCategory())) {
            queryWrapper.eq("category", postQueryRequest.getCategory());
        }

        queryWrapper.orderByDesc("createTime");

        // 执行分页查询
        Page<Post> postPage = this.page(
                new Page<>(postQueryRequest.getCurrent(), postQueryRequest.getPageSize()),
                queryWrapper);

        // 转换为VO并填充信息
        Page<PostVO> postVOPage = new Page<>();
        BeanUtils.copyProperties(postPage, postVOPage);
        // 【修正点4】正确转换Post实体到PostVO
        List<PostVO> postVOList = postPage.getRecords().stream().map(PostVO::objToVo).collect(Collectors.toList());

        fillPostsInfo(postVOList, loginUser);

        postVOPage.setRecords(postVOList);
        return postVOPage;
    }

    /**
     * 获取帖子榜单
     */
    @Override
    public List<PostVO> getTop100Post(Long type) {
        return getTop100Post(type.longValue(), null);
    }

    /**
     * 获取帖子榜单（带请求检测）
     */
    private List<PostVO> getTop100Post(long type, HttpServletRequest request) {
        // 如果有请求对象，进行爬虫检测
        if (request != null) {
            crawlerDetect(request);
        }

        // 构建查询条件
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("isDelete", 0)
                .eq("status", 1); // 只查询已审核通过的帖子

        // 根据类型设置时间范围
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);

        switch ((int) type) {
            case 1: // 日榜
                calendar.add(Calendar.DATE, -1);
                queryWrapper.ge("createTime", calendar.getTime());
                break;
            case 2: // 周榜
                calendar.add(Calendar.DATE, -7);
                queryWrapper.ge("createTime", calendar.getTime());
                break;
            case 3: // 月榜
                calendar.add(Calendar.MONTH, -1);
                queryWrapper.ge("createTime", calendar.getTime());
                break;
            case 4: // 总榜
                break;
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 按照浏览量、点赞数、评论数排序
        queryWrapper.orderByDesc("viewCount", "likeCount", "commentCount");

        // 限制返回100条
        queryWrapper.last("LIMIT 100");

        List<Post> posts = list(queryWrapper);

        // 转换为VO并填充信息
        // 【修正点5】正确转换Post实体到PostVO
        List<PostVO> postVOList = posts.stream().map(PostVO::objToVo).collect(Collectors.toList());
        fillPostsInfo(postVOList, null);

        return postVOList;
    }

    @Override
    public void fillPostInfo(PostVO postVO) {
        // 填充用户信息
        User user = userService.getById(postVO.getUserId());
        if (user != null) {
            postVO.setUser(userService.getUserVO(user));
        }

        // 获取实时浏览量（合并 Redis 中的增量）
        long realViewCount = getViewCount(postVO.getId());
        postVO.setViewCount(realViewCount);

        // 清空内容，只在详情页显示
        postVO.setContent(null);
    }

    @Override
    public long getViewCount(Long postId) {
        // 先从 Redis 获取增量
        String viewCountKey = String.format("post:viewCount:%d", postId);
        String incrementCount = stringRedisTemplate.opsForValue().get(viewCountKey);

        // 从数据库获取基础浏览量
        Post post = this.getById(postId);
        if (post == null) {
            return 0L;
        }

        // 合并数据库和 Redis 的浏览量
        long baseCount = post.getViewCount() != null ? post.getViewCount() : 0L;
        long increment = incrementCount != null ? Long.parseLong(incrementCount) : 0L;

        return baseCount + increment;
    }

    /**
     * 异步增加帖子浏览量
     */
    @Async("asyncExecutor")
    public void incrementViewCount(Long postId, HttpServletRequest request) {
        // 检查是否需要增加浏览量
        if (!crawlerManager.detectViewRequest(request, postId)) {
            return;
        }

        // 使用 Redis 进行计数
        String viewCountKey = String.format("post:viewCount:%d", postId);
        String lockKey = String.format("post:viewCount:lock:%d", postId);

        try {
            // 获取分布式锁
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                // 增加浏览量
                stringRedisTemplate.opsForValue().increment(viewCountKey);

                // 当浏览量达到一定阈值时，更新数据库
                String viewCountStr = stringRedisTemplate.opsForValue().get(viewCountKey);
                if (viewCountStr != null && Long.parseLong(viewCountStr) % 100 == 0) { // 改为100，和图片保持一致
                    this.update()
                            .setSql("viewCount = viewCount + " + viewCountStr)
                            .eq("id", postId)
                            .update();

                    // 更新后重置 Redis 计数
                    stringRedisTemplate.delete(viewCountKey);
                }
            }
        } finally {
            // 释放锁
            stringRedisTemplate.delete(lockKey);
        }
    }

    @Override
    public void addPostViewRecord(long postId, long userId, HttpServletRequest request) {
        try {
            ViewRecordAddRequest viewRecordAddRequest = new ViewRecordAddRequest();
            viewRecordAddRequest.setUserId(userId);
            viewRecordAddRequest.setTargetId(postId);
            viewRecordAddRequest.setTargetType(2); // 2-帖子

            viewRecordService.addViewRecord(viewRecordAddRequest, request);

            // 触发异步热点分数增量更新追踪
            postScoreUpdateTracker.addPostToHotScoreUpdateQueue(postId);
        } catch (Exception e) {
            log.error("添加帖子浏览记录失败", e);
        }
    }

    @Override
    public PostTagCategory listPostTagCategory(User loginUser) {
        String cacheKey = "post:tag_category:list";

        // 如果用户已登录，使用用户私有缓存
        if (loginUser != null) {
            cacheKey = "post:tag_category:list:" + loginUser.getId();
        }

        // 尝试从缓存获取
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                // 反序列化缓存数据
                PostTagCategory cachedData = cn.hutool.json.JSONUtil.toBean(cachedJson, PostTagCategory.class);
                return cachedData;
            } catch (Exception e) {
                log.warn("获取帖子标签分类缓存失败: {}", e.getMessage());
            }
        }

        // 缓存未命中，查询数据库默认全局分类和标签
        List<String> tagList = tagService.listTag();
        // 获取帖子分类（类型为1的分类）
        List<String> categoryList = categoryService.listCategoryByType(1);

        // 如果用户已登录，进行个性化排序
        if (loginUser != null) {
            try {
                Map<String, Integer> tagWeightMap = new HashMap<>();
                Map<String, Integer> categoryWeightMap = new HashMap<>();
                Long userId = loginUser.getId();
                log.info("[帖子个性化推荐] 开始计算用户 {} 的分类权重", userId);

                // 获取近期交互记录，包含目标类型 2（帖子）

                // 1. 获取近期收藏
                List<FavoriteRecord> favorites = favoriteRecordService.list(new QueryWrapper<FavoriteRecord>()
                        .eq("userId", userId)
                        .eq("targetType", 2)
                        .orderByDesc("createTime")
                        .last("limit 20"));
                log.info("[帖子个性化推荐] 用户 {} 近期收藏帖子 {} 条", userId, favorites.size());

                // 2. 获取近期点赞
                List<LikeRecord> likes = likeRecordService.list(new QueryWrapper<LikeRecord>()
                        .eq("userId", userId)
                        .eq("targetType", 2)
                        .orderByDesc("lastLikeTime")
                        .last("limit 20"));
                log.info("[帖子个性化推荐] 用户 {} 近期点赞帖子 {} 条", userId, likes.size());

                // 3. 获取近期浏览
                List<ViewRecord> views = viewRecordService.list(new QueryWrapper<ViewRecord>()
                        .eq("userId", userId)
                        .eq("targetType", 2)
                        .orderByDesc("updateTime")
                        .last("limit 50"));
                log.info("[帖子个性化推荐] 用户 {} 近期浏览帖子 {} 条", userId, views.size());

                // 统一收集帖子ID
                Set<Long> postIds = new HashSet<>();
                favorites.forEach(f -> postIds.add(f.getTargetId()));
                likes.forEach(l -> postIds.add(l.getTargetId()));
                views.forEach(v -> postIds.add(v.getTargetId()));

                // 批量查询帖子信息
                if (!postIds.isEmpty()) {
                    List<Post> postList = this.listByIds(postIds);
                    Map<Long, Post> postMap = postList.stream().collect(Collectors.toMap(Post::getId, p -> p));

                    // 计算权重 (浏览=1, 点赞=2, 收藏=3)
                    views.forEach(v -> addWeight(postMap.get(v.getTargetId()), tagWeightMap, categoryWeightMap, 1));
                    likes.forEach(l -> addWeight(postMap.get(l.getTargetId()), tagWeightMap, categoryWeightMap, 2));
                    favorites.forEach(f -> addWeight(postMap.get(f.getTargetId()), tagWeightMap, categoryWeightMap, 3));
                    log.info("[帖子个性化推荐] 用户 {} categoryWeightMap={}", userId, categoryWeightMap);

                    // 对默认列表进行降序排序
                    tagList.sort((a, b) -> tagWeightMap.getOrDefault(b, 0) - tagWeightMap.getOrDefault(a, 0));
                    categoryList.sort(
                            (a, b) -> categoryWeightMap.getOrDefault(b, 0) - categoryWeightMap.getOrDefault(a, 0));
                    log.info("[帖子个性化推荐] 用户 {} 排序后 categoryList={}", userId, categoryList);
                }
            } catch (Exception e) {
                log.error("计算用户个性化帖子分类推荐失败", e);
            }
        }

        PostTagCategory postTagCategory = new PostTagCategory();
        postTagCategory.setTagList(tagList);
        postTagCategory.setCategoryList(categoryList);

        // 将结果存入缓存 (公共缓存1小时，私有缓存5分钟以提升更新率)
        try {
            long expireTime = loginUser != null ? 300 : 3600;
            stringRedisTemplate.opsForValue().set(cacheKey, cn.hutool.json.JSONUtil.toJsonStr(postTagCategory),
                    expireTime, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("设置帖子标签分类缓存失败: {}", e.getMessage());
        }

        return postTagCategory;
    }

    /**
     * 为标签和分类添加交互权重分数
     */
    private void addWeight(Post p, Map<String, Integer> tagWeightMap, Map<String, Integer> categoryWeightMap,
                           int weight) {
        if (p == null)
            return;

        // 累加分类
        if (StrUtil.isNotBlank(p.getCategory())) {
            categoryWeightMap.put(p.getCategory(), categoryWeightMap.getOrDefault(p.getCategory(), 0) + weight);
        }

        // 累加标签
        if (StrUtil.isNotBlank(p.getTags())) {
            try {
                List<String> tags = cn.hutool.json.JSONUtil.toList(p.getTags(), String.class);
                for (String tag : tags) {
                    tagWeightMap.put(tag, tagWeightMap.getOrDefault(tag, 0) + weight);
                }
            } catch (Exception ignored) {
                // 如果解析失败则尝试使用逗号分隔
                String[] tags = p.getTags().split(",");
                for (String tag : tags) {
                    if (StrUtil.isNotBlank(tag)) {
                        tagWeightMap.put(tag.trim(), tagWeightMap.getOrDefault(tag.trim(), 0) + weight);
                    }
                }
            }
        }
    }

    @Override
    public Long saveDraft(PostAddRequest postAddRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(postAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 创建帖子草稿
        Post post = new Post();
        post.setTitle(postAddRequest.getTitle());
        post.setContent(postAddRequest.getContent());
        post.setCategory(postAddRequest.getCategory());
        post.setUserId(loginUser.getId());

        // 设置封面图，直接使用前端传递的封面图URL
        String coverUrl = postAddRequest.getCoverUrl();
        log.info("保存草稿时设置封面图，URL: {}", coverUrl);
        post.setCoverUrl(coverUrl);

        // 设置标签，直接使用前端传递的标签
        if (postAddRequest.getTags() != null) {
            post.setTags(cn.hutool.json.JSONUtil.toJsonStr(postAddRequest.getTags()));
        } else {
            post.setTags(null); // 如果前端传空值，则清空标签
        }

        // 设置为草稿状态
        post.setIsDraft(1); // 1表示草稿
        post.setStatus(0); // 草稿状态下状态为0

        log.info("准备保存草稿 - ID: {}, 标题: {}, 封面图: {}", post.getId(), post.getTitle(), post.getCoverUrl());

        // 保存草稿
        boolean success = this.save(post);
        ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR);

        // 验证草稿保存结果
        Post savedDraft = this.getById(post.getId());
        log.info("草稿保存后从数据库读取 - ID: {}, 封面图: {}, 标题: {}",
                savedDraft.getId(), savedDraft.getCoverUrl(), savedDraft.getTitle());

        return post.getId();
    }

    @Override
    public Long saveOrUpdateDraft(PostAddRequest postAddRequest, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(postAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 检查是否提供了ID，如果有ID则尝试更新现有草稿
        if (postAddRequest.getId() != null) {
            // 获取原草稿信息
            Post oldDraft = this.getById(postAddRequest.getId());
            ThrowUtils.throwIf(oldDraft == null, ErrorCode.NOT_FOUND_ERROR, "草稿不存在");

            // 校验权限 - 只有本人可以更新自己的草稿
            if (!oldDraft.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }

            // 校验是否确实是草稿
            if (oldDraft.getIsDraft() == null || oldDraft.getIsDraft() != 1) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "该帖子不是草稿，无法更新");
            }

            // 更新草稿信息
            Post updateDraft = new Post();
            updateDraft.setId(postAddRequest.getId());
            updateDraft.setTitle(postAddRequest.getTitle());
            updateDraft.setContent(postAddRequest.getContent());
            updateDraft.setCategory(postAddRequest.getCategory());

            // 设置封面图，直接使用前端传递的封面图URL
            updateDraft.setCoverUrl(postAddRequest.getCoverUrl());

            // 设置标签，直接使用前端传递的标签
            if (postAddRequest.getTags() != null) {
                updateDraft.setTags(cn.hutool.json.JSONUtil.toJsonStr(postAddRequest.getTags()));
            } else {
                updateDraft.setTags(null); // 如果前端传空值，则清空标签
            }

            // 确保仍然是草稿状态
            updateDraft.setIsDraft(1); // 1表示草稿
            updateDraft.setStatus(0); // 草稿状态下状态为0

            // 更新草稿
            boolean success = this.updateById(updateDraft);
            ThrowUtils.throwIf(!success, ErrorCode.OPERATION_ERROR);

            return updateDraft.getId();
        } else {
            // 没有ID，创建新草稿
            return saveDraft(postAddRequest, loginUser);
        }
    }

    @Override
    public List<PostVO> listDrafts(User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);

        // 查询用户的草稿列表
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", loginUser.getId())
                .eq("isDraft", 1) // 只查询草稿
                .eq("isDelete", 0) // 未删除的
                .orderByDesc("updateTime"); // 按更新时间倒序

        List<Post> posts = this.list(queryWrapper);

        // 转换为 VO
        return posts.stream().map(PostVO::objToVo).collect(Collectors.toList());
    }

    @Override
    public Boolean deleteDraft(Long draftId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(draftId == null, ErrorCode.PARAMS_ERROR);

        // 查询草稿是否存在
        Post draft = this.getById(draftId);
        ThrowUtils.throwIf(draft == null, ErrorCode.PARAMS_ERROR, "草稿不存在");

        // 校验是否为当前用户创建的草稿
        ThrowUtils.throwIf(!draft.getUserId().equals(loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权删除此草稿");

        // 校验是否为草稿状态
        ThrowUtils.throwIf(draft.getIsDraft() == null || draft.getIsDraft() != 1,
                ErrorCode.PARAMS_ERROR, "该帖子不是草稿，不能删除");

        // 逻辑删除草稿（使用 removeById 配合 @TableLogic 注解）
        return this.removeById(draftId);
    }

    @Override
    public boolean setPostPermission(Long postId, Long userId, Integer allowCollect, Integer allowLike,
                                     Integer allowComment, Integer allowShare) {
        // 1. 校验参数
        if (postId == null || userId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "帖子ID和用户ID不能为空");
        }

        // 2. 检查帖子是否存在
        Post post = this.getById(postId);
        if (post == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        }

        // 3. 权限校验：只有帖子作者或管理员可以设置权限
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        }

        if (!userService.isAdmin(user) && !post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有权限设置帖子权限");
        }

        // 4. 校验参数值的有效性
        if (allowCollect != null && !asList(0, 1).contains(allowCollect)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "允许收藏参数无效");
        }

        if (allowLike != null && !asList(0, 1).contains(allowLike)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "允许点赞参数无效");
        }

        if (allowComment != null && !asList(0, 1).contains(allowComment)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "允许评论参数无效");
        }

        if (allowShare != null && !asList(0, 1).contains(allowShare)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "允许分享参数无效");
        }

        // 5. 构建更新对象
        Post updatePost = new Post();
        updatePost.setId(postId);

        if (allowCollect != null) {
            updatePost.setAllowCollect(allowCollect);
        }
        if (allowLike != null) {
            updatePost.setAllowLike(allowLike);
        }
        if (allowComment != null) {
            updatePost.setAllowComment(allowComment);
        }
        if (allowShare != null) {
            updatePost.setAllowShare(allowShare);
        }

        // 6. 更新数据库
        boolean result = this.updateById(updatePost);

        // 7. 清除相关缓存
        if (result) {
            // 清除该帖子的缓存
            String postCacheKey = String.format("post:detail:%d", postId);
            stringRedisTemplate.delete(postCacheKey);

            // 清除可能影响的列表缓存
            String listCacheKeyPattern = "post:list:*";
            Set<String> keys = stringRedisTemplate.keys(listCacheKeyPattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        }

        return result;
    }

    @Override
    public boolean checkPostPermission(Post post, String operation) {
        if (post == null) {
            return false;
        }

        // 根据操作类型检查权限
        switch (operation) {
            case "collect":
                return post.getAllowCollect() != null && post.getAllowCollect() == 1;
            case "like":
                return post.getAllowLike() != null && post.getAllowLike() == 1;
            case "comment":
                return post.getAllowComment() != null && post.getAllowComment() == 1;
            case "share":
                return post.getAllowShare() != null && post.getAllowShare() == 1;
            default:
                return false;
        }
    }

    // 辅助方法，用于创建包含指定元素的列表
    private static <T> List<T> asList(T... elements) {
        return Arrays.asList(elements);
    }

    @Override
    public Page<PostVO> listPostVOByRecommend(long current, long size, HttpServletRequest request) {
        User loginUser = userService.isLogin(request);

        // 1. 获取全站总数（用于分页基础）
        String countCacheKey = "post:public:total_count";
        Long totalPublicCount = Optional.ofNullable(stringRedisTemplate.opsForValue().get(countCacheKey))
                .map(Long::valueOf)
                .orElseGet(() -> {
                    QueryWrapper<Post> baseQuery = new QueryWrapper<>();
                    baseQuery.eq("status", 1)
                            .eq("isDraft", 0)
                            .eq("isDelete", 0);
                    long count = this.count(baseQuery);
                    stringRedisTemplate.opsForValue().set(countCacheKey, String.valueOf(count), 300, TimeUnit.SECONDS);
                    return count;
                });

        // 2. 构建推荐池
        RecommendationPool pool = buildRecommendationPool(loginUser, totalPublicCount);

        // 3. 从池中分页获取 ID
        long startOffset = (current - 1) * size;
        List<Long> pagedIds = pool.getPage(startOffset, size);

        if (CollUtil.isEmpty(pagedIds)) {
            return new Page<>(current, size, pool.getSize());
        }

        // 4. 按推荐顺序查询数据库
        String idStr = StrUtil.join(",", pagedIds);
        List<Post> posts = this.lambdaQuery()
                .in(Post::getId, pagedIds)
                .last("ORDER BY FIELD(id, " + idStr + ")")
                .list();

        Page<PostVO> resultPage = new Page<>(current, size, pool.getSize());
        List<PostVO> postVOList = posts.stream()
                .map(PostVO::objToVo)
                .collect(Collectors.toList());

        // 5. 填充额外信息
        fillPostsInfo(postVOList, loginUser);

        // 对于推荐列表，我们通常不希望在列表页返回详情内容以节省带宽
        postVOList.forEach(vo -> vo.setContent(null));

        resultPage.setRecords(postVOList);
        return resultPage;
    }

    /**
     * 构建帖子推荐池
     */
    private RecommendationPool buildRecommendationPool(User loginUser, long totalCount) {
        List<Long> cfIds = new ArrayList<>();
        List<Long> historyIds = new ArrayList<>();
        List<Long> hotIds = new ArrayList<>();

        if (loginUser != null) {
            // 1. 获取用户历史记录（带缓存）
            String historyCacheKey = "user:post:history:" + loginUser.getId();
            List<String> cachedHistory = stringRedisTemplate.opsForList().range(historyCacheKey, 0, 199);

            if (CollUtil.isNotEmpty(cachedHistory)) {
                historyIds = cachedHistory.stream().map(Long::valueOf).collect(Collectors.toList());
            } else {
                QueryWrapper<ViewRecord> historyQuery = new QueryWrapper<>();
                historyQuery.eq("userId", loginUser.getId())
                        .eq("targetType", 2) // 帖子
                        .orderByDesc("updateTime")
                        .last("limit 200")
                        .select("targetId");
                historyIds = viewRecordService.list(historyQuery).stream()
                        .map(ViewRecord::getTargetId)
                        .distinct()
                        .collect(Collectors.toList());

                if (CollUtil.isNotEmpty(historyIds)) {
                    stringRedisTemplate.opsForList().rightPushAll(historyCacheKey,
                            historyIds.stream().map(String::valueOf).collect(Collectors.toList()));
                    stringRedisTemplate.expire(historyCacheKey, 600, TimeUnit.SECONDS);
                }
            }

            // 2. 获取 CF 推荐列表（带缓存）
            String cfCacheKey = "user:post:cf:" + loginUser.getId();
            List<String> cachedCf = stringRedisTemplate.opsForList().range(cfCacheKey, 0, -1);

            if (CollUtil.isNotEmpty(cachedCf)) {
                cfIds = cachedCf.stream().map(Long::valueOf).collect(Collectors.toList());
            } else {
                List<Long> rawCfIds = recommendationManager.getPostCFRecommendationList(loginUser.getId());
                if (CollUtil.isNotEmpty(rawCfIds)) {
                    Set<Long> historySet = new HashSet<>(historyIds);
                    cfIds = rawCfIds.stream()
                            .filter(id -> !historySet.contains(id))
                            .collect(Collectors.toList());

                    // 缓存 CF 推荐结果（5分钟）
                    if (CollUtil.isNotEmpty(cfIds)) {
                        stringRedisTemplate.opsForList().rightPushAll(cfCacheKey,
                                cfIds.stream().map(String::valueOf).collect(Collectors.toList()));
                        stringRedisTemplate.expire(cfCacheKey, 300, TimeUnit.SECONDS);
                    }
                }
            }
        }

        // 3. 构建热门补充池（带缓存）
        String hotCacheKey = "post:hot:pool";
        List<String> cachedHot = stringRedisTemplate.opsForList().range(hotCacheKey, 0, -1);

        if (CollUtil.isNotEmpty(cachedHot)) {
            hotIds = cachedHot.stream().map(Long::valueOf).collect(Collectors.toList());
        } else {
            // 限制热门池大小，避免内存占用过大
            int hotPoolLimit = Math.min(5000, (int) totalCount);
            QueryWrapper<Post> hotQuery = new QueryWrapper<>();
            hotQuery.eq("status", 1)
                    .eq("isDraft", 0)
                    .eq("isDelete", 0)
                    .orderByDesc("hotScore", "createTime")
                    .last("LIMIT " + hotPoolLimit)
                    .select("id");

            hotIds = this.list(hotQuery).stream()
                    .map(Post::getId)
                    .collect(Collectors.toList());

            // 缓存热门池（10分钟）
            if (CollUtil.isNotEmpty(hotIds)) {
                stringRedisTemplate.opsForList().rightPushAll(hotCacheKey,
                        hotIds.stream().map(String::valueOf).collect(Collectors.toList()));
                stringRedisTemplate.expire(hotCacheKey, 600, TimeUnit.SECONDS);
            }
        }

        log.info("帖子推荐池构建完成 - CF:{}, 历史:{}, 热门:{}", cfIds.size(), historyIds.size(), hotIds.size());

        return new RecommendationPool(cfIds, historyIds, hotIds);
    }

    /**
     * 推荐池辅助类
     */
    private static class RecommendationPool {
        private final List<Long> mergedPool;

        public RecommendationPool(List<Long> cfIds, List<Long> historyIds, List<Long> hotIds) {
            // 顺序：CF推荐 -> 热门补充 -> 历史回顾
            // 使用 LinkedHashSet 进行去重并保持推荐顺序
            Set<Long> uniquePool = new LinkedHashSet<>();
            uniquePool.addAll(cfIds);
            uniquePool.addAll(hotIds);
            uniquePool.addAll(historyIds);
            this.mergedPool = new ArrayList<>(uniquePool);
        }

        /**
         * 获取池的总大小
         */
        public long getSize() {
            return mergedPool.size();
        }

        public List<Long> getPage(long offset, long size) {
            if (offset >= mergedPool.size()) {
                return Collections.emptyList();
            }

            int start = (int) offset;
            int end = Math.min(start + (int) size, mergedPool.size());
            return mergedPool.subList(start, end);
        }
    }

    @Override
    public long countPostScoreData() {
        return this.baseMapper.countPostScoreData();
    }

    @Override
    public List<com.lumenglover.yuemupicturebackend.model.dto.post.PostHotScoreDto> selectPostScoreData(long offset,
                                                                                                        long pageSize) {
        return this.baseMapper.selectPostScoreData(offset, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateBatchHotScore(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return false;
        }

        // 将大批次拆分成小批次以减小事务粒度
        int batchSize = 50;
        for (int i = 0; i < posts.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, posts.size());
            List<Post> batch = posts.subList(i, endIndex);

            boolean result = this.updateBatchById(batch);
            if (!result) {
                return false;
            }
        }

        return true;
    }

    @Override
    public Long selectMaxPostId() {
        return this.baseMapper.selectMaxPostId();
    }

    @Override
    public List<com.lumenglover.yuemupicturebackend.model.dto.post.PostHotScoreDto> selectPostScoreDataInRange(
            long minId, long maxId, long offset, long pageSize) {
        return this.baseMapper.selectPostScoreDataInRange(minId, maxId, offset, pageSize);
    }

    @Override
    public List<Post> listPostsForHotScore(long offset, int limit) {
        QueryWrapper<Post> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("status", 1) // 审核通过
                .eq("isDelete", 0) // 未删除
                .eq("isDraft", 0) // 非草稿
                .select("id", "viewCount", "likeCount", "commentCount", "shareCount", "createTime")
                .orderByAsc("id") // 按ID排序，便于分页
                .last("LIMIT " + offset + ", " + limit);

        return this.list(queryWrapper);
    }

    /**
     * 自动生成总结评论（如果帖子状态为审核通过）
     */
    private void generateSummaryCommentIfApproved(Long postId) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            doGenerateSummaryComment(postId);
                        }
                    });
        } else {
            doGenerateSummaryComment(postId);
        }
    }

    private void doGenerateSummaryComment(Long postId) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                log.info("[AI总结] 开始处理帖子ID: {}", postId);
                Post post = this.getById(postId);
                if (post == null) {
                    log.warn("[AI总结] 帖子 {} 不存在，跳过", postId);
                    return;
                }
                if (post.getStatus() == null || post.getStatus() != 1) {
                    log.warn("[AI总结] 帖子 {} 状态不是已通过(status={})，跳过", postId, post.getStatus());
                    return;
                }
                User botUser = userService
                        .getOne(new QueryWrapper<User>().eq("userAccount", pexelsConfig.getBot().getUserAccount()));
                if (botUser == null) {
                    log.warn("[AI总结] 未找到机器人账号，跳过");
                    return;
                }
                // 检查是否已评论过
                long count = commentsService.count(new QueryWrapper<Comments>()
                        .eq("targetId", post.getId())
                        .eq("targetType", 2)
                        .eq("userId", botUser.getId()));
                if (count > 0) {
                    log.info("[AI总结] 帖子 {} 已有机器人评论，跳过", postId);
                    return;
                }
                String summary = "这篇帖子分享了精彩的内容，欢迎大家一起讨论！";
                try {
                    String contentToSummarize = post.getTitle() + "\n" + post.getContent();
                    String cleanContent = contentToSummarize.replaceAll("<[^>]+>", "").trim();
                    if (cleanContent.length() > 50) {
                        String endpoint = ragConfig.getPythonService().getBaseUrl()
                                + ragConfig.getPythonService().getSummarizeEndpoint();
                        java.util.Map<String, Object> reqBody = new java.util.HashMap<>();
                        reqBody.put("question", cn.hutool.core.util.StrUtil.sub(cleanContent, 0, 1000));
                        log.info("[AI总结] 调用 Python 摆要接口: {}", endpoint);
                        String jsonResponse = cn.hutool.http.HttpUtil.post(endpoint,
                                cn.hutool.json.JSONUtil.toJsonStr(reqBody), 15000);
                        log.info("[AI总结] Python 返回: {}", jsonResponse);
                        cn.hutool.json.JSONObject resObj = cn.hutool.json.JSONUtil.parseObj(jsonResponse);
                        // Python /api/rag/summarize 接口成功返回 code=200
                        if (resObj.getInt("code", -1) == 200) {
                            String ans = resObj.getJSONObject("data").getStr("answer");
                            if (cn.hutool.core.util.StrUtil.isNotBlank(ans)) {
                                summary = ans;
                                log.info("[AI总结] 成功获取AI摘要。");
                            }
                        } else {
                            log.warn("[AI总结] Python 摆要接口返回非0 code: {}", resObj.getInt("code", -1));
                        }
                    } else {
                        summary = cn.hutool.core.util.StrUtil.sub(cleanContent, 0, 100) + "...";
                    }
                } catch (Exception e) {
                    log.error("[AI总结] 调用AI生成摘要失败", e);
                }
                String commentContent = "💡 AI总结：\n" + summary;
                Comments comments = new Comments();
                comments.setUserId(botUser.getId());
                comments.setTargetId(post.getId());
                comments.setTargetType(2);
                comments.setTargetUserId(post.getUserId());
                comments.setContent(commentContent);
                commentsService.save(comments);
                log.info("[AI总结] 帖子 {} 总结评论已保存。", postId);
            } catch (Exception e) {
                log.error("[AI总结] 生成帖子总结评论失败", e);
            }
        });
    }
}
