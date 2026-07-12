package com.lumenglover.yuemupicturebackend.model.vo;

import cn.hutool.json.JSONUtil;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.utils.DomainReplaceUtil;
import com.lumenglover.yuemupicturebackend.utils.VoUrlReplaceUtil;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class PictureVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 url
     */
    private String url;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 标签
     */
    private List<String> tags;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 分类
     */
    private String category;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 评论数
     */
    private Long commentCount;

    /**
     * 点赞数
     */
    private Long likeCount;

    /**
     * 分享数
     */
    private Long shareCount;

    /**
     * 收藏数
     */
    private Long favoriteCount;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 当前用户是否点赞
     */
    private Integer isLiked;

    /**
     * 当前用户是否分享
     */
    private Integer isShared;

    /**
     * 当前用户是否收藏
     */
    private Integer isFavorited;

    /**
     * 创建用户信息
     */
    private UserVO user;

    /**
     * 权限列表
     */
    private List<String> permissionList = new ArrayList<>();

    /**
     * 是否允许收藏
     */
    private Boolean allowCollect;

    /**
     * 是否允许点赞
     */
    private Boolean allowLike;

    /**
     * 是否允许评论
     */
    private Boolean allowComment;

    /**
     * 是否允许分享
     */
    private Boolean allowShare;


    /**
     * 浏览量
     */
    private Long viewCount;

    /**
     * 聊天总数
     */
    private Long chatCount;

    /**
     * 是否精选
     */
    private Integer isFeature;

    /**
     * 是否允许下载：0-禁止下载 1-允许下载
     */
    private Integer isDownload;

    private Double recommendScore;

    /**
     * AI 自动识别标签
     */
    private List<String> aiLabels;

    /**
     * 是否为草稿：0-非草稿 1-草稿
     */
    private Integer isDraft;


    private static final long serialVersionUID = 1L;

    /**
     * 封装类转对象
     */
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        picture.setAiLabels(JSONUtil.toJsonStr(pictureVO.getAiLabels()));
        return picture;
    }

    /**
     * 对象转封装类
     */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        // 类型不同，需要转换（增加 null 和异常保护，避免标签非 JSON 格式时拖垮服务）
        if (cn.hutool.core.util.StrUtil.isNotBlank(picture.getTags())) {
            try {
                pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
            } catch (Exception e) {
                pictureVO.setTags(new ArrayList<>());
            }
        } else {
            pictureVO.setTags(new ArrayList<>());
        }

        if (cn.hutool.core.util.StrUtil.isNotBlank(picture.getAiLabels())) {
            try {
                pictureVO.setAiLabels(JSONUtil.toList(picture.getAiLabels(), String.class));
            } catch (Exception e) {
                pictureVO.setAiLabels(new ArrayList<>());
            }
        } else {
            pictureVO.setAiLabels(new ArrayList<>());
        }

        // 替换URL为自定义域名
        pictureVO.setUrl(VoUrlReplaceUtil.replaceUrl(pictureVO.getUrl()));
        pictureVO.setThumbnailUrl(VoUrlReplaceUtil.replaceUrl(pictureVO.getThumbnailUrl()));

        // 设置权限相关字段
        pictureVO.setAllowCollect(picture.getAllowCollect() != null && picture.getAllowCollect() == 1);
        pictureVO.setAllowLike(picture.getAllowLike() != null && picture.getAllowLike() == 1);
        pictureVO.setAllowComment(picture.getAllowComment() != null && picture.getAllowComment() == 1);
        pictureVO.setAllowShare(picture.getAllowShare() != null && picture.getAllowShare() == 1);

        return pictureVO;
    }
}
