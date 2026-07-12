package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.post.PostAddRequest;
import com.lumenglover.yuemupicturebackend.model.dto.post.PostQueryRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Post;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.PostTagCategory;
import com.lumenglover.yuemupicturebackend.model.vo.PostVO;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

public interface PostService extends IService<Post> {
    /**
     * 发布帖子
     */
    Long addPost(PostAddRequest postAddRequest, User loginUser);


    /**
     * 分页获取帖子列表
     */
    Page<PostVO> listPosts(PostQueryRequest request, User loginUser);

    /**
     * 审核帖子
     */
    void reviewPost(Long postId, Integer status, String message, User loginUser);

    Page<PostVO> listMyPosts(PostQueryRequest postQueryRequest);

    boolean updatePost(Post post);

    /**
     * 获取关注用户的帖子列表
     */
    Page<PostVO> getFollowPosts(HttpServletRequest request, PostQueryRequest postQueryRequest);

    /**
     * 获取帖子榜单
     * @param id 榜单类型：1-日榜 2-周榜 3-月榜 4-总榜
     */
    List<PostVO> getTop100Post(Long id);

    void fillPostInfo(PostVO postVO);

    /**
     * 获取帖子浏览量
     */
    long getViewCount(Long postId);

    Map<String, Object> getPostStatusStats();

    /**
     * 获取帖子详情（带浏览量统计）
     */
    PostVO getPostDetail(Long id, User loginUser, HttpServletRequest request);

    /**
     * 添加帖子浏览记录
     * @param postId 帖子ID
     * @param userId 用户ID
     * @param request HTTP请求
     */
    void addPostViewRecord(long postId, long userId, HttpServletRequest request);

    PostTagCategory listPostTagCategory(User loginUser);

    /**
     * 保存帖子草稿
     */
    Long saveDraft(PostAddRequest postAddRequest, User loginUser);

    /**
     * 保存或更新帖子草稿
     */
    Long saveOrUpdateDraft(PostAddRequest postAddRequest, User loginUser);

    /**
     * 获取用户的草稿列表
     */
    List<PostVO> listDrafts(User loginUser);

    /**
     * 删除草稿
     */
    Boolean deleteDraft(Long draftId, User loginUser);

    /**
     * 设置帖子权限
     *
     * @param postId 帖子ID
     * @param userId 用户ID
     * @param allowCollect 是否允许收藏
     * @param allowLike 是否允许点赞
     * @param allowComment 是否允许评论
     * @param allowShare 是否允许分享
     * @return 操作结果
     */
    boolean setPostPermission(Long postId, Long userId, Integer allowCollect, Integer allowLike, Integer allowComment, Integer allowShare);

    /**
     * 检查帖子权限
     *
     * @param post 帖子
     * @param operation 操作类型（"collect", "like", "comment", "share"）
     * @return 是否有权限
     */
    boolean checkPostPermission(Post post, String operation);

    /**
     * 分页获取推荐帖子列表
     */
    Page<PostVO> listPostVOByRecommend(long current, long size, HttpServletRequest request);

    /**
     * 统计需要计算热榜分数的帖子数量
     * @return 数量
     */
    long countPostScoreData();

    /**
     * 分页查询用于计算热榜分数的帖子数据
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 帖子数据列表
     */
    List<com.lumenglover.yuemupicturebackend.model.dto.post.PostHotScoreDto> selectPostScoreData(long offset, long pageSize);

    /**
     * 批量更新帖子热榜分数
     * @param posts 帖子列表
     * @return 是否成功
     */
    boolean updateBatchHotScore(List<Post> posts);

    /**
     * 获取最大的帖子ID
     * @return 最大ID
     */
    Long selectMaxPostId();


    /**
     * 分页查询用于计算热榜分数的帖子数据
     * 只查询审核通过且非草稿的帖子
     *
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 帖子列表
     */
    List<Post> listPostsForHotScore(long offset, int limit);

    /**
     * 按ID范围分页查询用于计算热榜分数的帖子数据
     * @param minId 最小ID
     * @param maxId 最大ID
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 帖子数据列表
     */
    List<com.lumenglover.yuemupicturebackend.model.dto.post.PostHotScoreDto> selectPostScoreDataInRange(long minId, long maxId, long offset, long pageSize);
}
