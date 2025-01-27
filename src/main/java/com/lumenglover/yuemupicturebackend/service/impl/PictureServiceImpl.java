package com.lumenglover.yuemupicturebackend.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.api.aliyunai.AliYunAiApi;
import com.lumenglover.yuemupicturebackend.api.aliyunai.model.CreateOutPaintingTaskRequest;
import com.lumenglover.yuemupicturebackend.api.aliyunai.model.CreateOutPaintingTaskResponse;
import com.lumenglover.yuemupicturebackend.constant.CrawlerConstant;
import com.lumenglover.yuemupicturebackend.model.dto.es.EsPictureDao;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.ThrowUtils;
import com.lumenglover.yuemupicturebackend.manager.CosManager;
import com.lumenglover.yuemupicturebackend.manager.CounterManager;
import com.lumenglover.yuemupicturebackend.manager.FileManager;
import com.lumenglover.yuemupicturebackend.manager.upload.FilePictureUpload;
import com.lumenglover.yuemupicturebackend.manager.upload.PictureUploadTemplate;
import com.lumenglover.yuemupicturebackend.manager.upload.UrlPictureUpload;
import com.lumenglover.yuemupicturebackend.mapper.PictureMapper;
import com.lumenglover.yuemupicturebackend.model.dto.file.UploadPictureResult;
import com.lumenglover.yuemupicturebackend.model.dto.picture.*;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.Picturelike;
import com.lumenglover.yuemupicturebackend.model.entity.Space;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.entity.es.EsPicture;
import com.lumenglover.yuemupicturebackend.model.enums.OperationEnum;
import com.lumenglover.yuemupicturebackend.model.enums.PictureReviewStatusEnum;
import com.lumenglover.yuemupicturebackend.model.vo.PictureVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.*;
import com.lumenglover.yuemupicturebackend.utils.ColorSimilarUtils;
import com.lumenglover.yuemupicturebackend.utils.ColorTransformUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.awt.*;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @description 针对表【picture(图片)】的数据库操作Service实现
 * @createDate 2024-12-11 20:45:51
 */
@Slf4j
@Service
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    @Resource
    private FileManager fileManager;

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private UrlPictureUpload urlPictureUpload;

    @Autowired
    private CosManager cosManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private AliYunAiApi aliYunAiApi;

    @Resource
    private PicturelikeService picturelikeService;

    @Resource
    private UserfollowsService userfollowsService;

    @Resource
    private CounterManager counterManager;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private EsPictureDao esPictureDao;

    @Override
    public void validPicture(Picture picture) {
        ThrowUtils.throwIf(picture == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        Long id = picture.getId();
        String url = picture.getUrl();
        String introduction = picture.getIntroduction();
        // 修改数据时，id 不能为空，有参数则校验
        ThrowUtils.throwIf(ObjUtil.isNull(id), ErrorCode.PARAMS_ERROR, "id 不能为空");
        // 如果传递了 url，才校验
        if (StrUtil.isNotBlank(url)) {
            ThrowUtils.throwIf(url.length() > 1024, ErrorCode.PARAMS_ERROR, "url 过长");
        }
        if (StrUtil.isNotBlank(introduction)) {
            ThrowUtils.throwIf(introduction.length() > 800, ErrorCode.PARAMS_ERROR, "简介过长");
        }
    }

    @Override
    public PictureVO uploadPicture(Object inputSource, PictureUploadRequest pictureUploadRequest, User loginUser) {
        // 校验参数
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 校验空间是否存在
        Long spaceId = pictureUploadRequest.getSpaceId();
        if (spaceId != null) {
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            // 校验是否有空间的权限，仅空间管理员才能上传
//            if (!loginUser.getId().equals(space.getUserId())) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
//            }
            // 校验额度
            if (space.getTotalCount() >= space.getMaxCount()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间条数不足");
            }
            if (space.getTotalSize() >= space.getMaxSize()) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间大小不足");
            }
        }
        // 判断是新增还是删除
        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        // 如果是更新，判断图片是否存在
        if (pictureId != null) {
            Picture oldPicture = this.getById(pictureId);
            ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
//            // 仅本人或管理员可编辑图片
//            if (!oldPicture.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
//                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
//            }
            // 校验空间是否一致
            // 没传 spaceId，则复用原有图片的 spaceId（这样也兼容了公共图库）
            if (spaceId == null) {
                if (oldPicture.getSpaceId() != null) {
                    spaceId = oldPicture.getSpaceId();
                }
            } else {
                // 传了 spaceId，必须和原图片的空间 id 一致
                if (ObjUtil.notEqual(spaceId, oldPicture.getSpaceId())) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 id 不一致");
                }
            }
        }
        // 上传图片，得到图片信息
        // 按照用户 id 划分目录 => 按照空间划分目录
        String uploadPathPrefix;
        if (spaceId == null) {
            // 公共图库
            uploadPathPrefix = String.format("public/%s", loginUser.getId());
        } else {
            // 空间
            uploadPathPrefix = String.format("space/%s", spaceId);
        }
        // 根据 inputSource 的类型区分上传方式
        PictureUploadTemplate pictureUploadTemplate = filePictureUpload;
        if (inputSource instanceof String) {
            pictureUploadTemplate = urlPictureUpload;
        }
        UploadPictureResult uploadPictureResult = pictureUploadTemplate.uploadPicture(inputSource, uploadPathPrefix);
        // 构造要入库的图片信息
        Picture picture = new Picture();
        picture.setSpaceId(spaceId); // 指定空间 id
        picture.setUrl(uploadPictureResult.getUrl());
        picture.setThumbnailUrl(uploadPictureResult.getThumbnailUrl());
        // 支持外层传递图片名称
        String picName = uploadPictureResult.getPicName();
        if (pictureUploadRequest != null && StrUtil.isNotBlank(pictureUploadRequest.getPicName())) {
            picName = pictureUploadRequest.getPicName();
        }
        picture.setName(picName);
        picture.setPicSize(uploadPictureResult.getPicSize());
        picture.setPicWidth(uploadPictureResult.getPicWidth());
        picture.setPicHeight(uploadPictureResult.getPicHeight());
        picture.setPicScale(uploadPictureResult.getPicScale());
        picture.setPicFormat(uploadPictureResult.getPicFormat());
        // 转换为标准颜色
        picture.setPicColor(ColorTransformUtils.getStandardColor(uploadPictureResult.getPicColor()));
        picture.setCategory(pictureUploadRequest.getCategoryName());
        picture.setTags(pictureUploadRequest.getTagName());
        picture.setUserId(loginUser.getId());
        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        // 如果 pictureId 不为空，表示更新，否则是新增
        if (pictureId != null) {
            // 如果是更新，需要补充 id 和编辑时间
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        // 开启事务
        Long finalSpaceId = spaceId;
        transactionTemplate.execute(status -> {
            // 插入数据
            boolean result = this.saveOrUpdate(picture);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "图片上传失败，数据库操作失败");
            if (finalSpaceId != null) {
                // 更新空间的使用额度
                boolean update = spaceService.lambdaUpdate()
                        .eq(Space::getId, finalSpaceId)
                        .setSql("totalSize = totalSize + " + picture.getPicSize())
                        .setSql("totalCount = totalCount + 1")
                        .update();
                ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
            }
            return picture;
        });
        // 可自行实现，如果是更新，可以清理图片资源
        // this.clearPictureFile(oldPicture);
        return PictureVO.objToVo(picture);
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        // 对象转封装类
        PictureVO pictureVO = PictureVO.objToVo(picture);
        // 关联查询用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    /**
     * 分页获取图片封装
     */
    @Override
    public Page<PictureVO>  getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());
        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }
        // 对象列表 => 封装对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
        // 1. 关联查询用户信息
        // 1,2,3,4
        Set<Long> userIdSet = pictureList.stream().map(Picture::getUserId).collect(Collectors.toSet());
        // 1 => user1, 2 => user2
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        // 查询是否登录
        User currentUserId = userService.isLogin(request);
        Map<Long, List<Picturelike>> userIdIsLikeMap;
        // 查询是否点赞
        // 使用 QueryWrapper 进行条件查询，添加登录用户的条件
        if(currentUserId != null){
            Set<Long> pictureIdSet = pictureList.stream().map(Picture::getId).collect(Collectors.toSet());
            QueryWrapper<Picturelike> queryWrapper = new QueryWrapper<>();
            queryWrapper.in("pictureId", pictureIdSet);
            queryWrapper.eq("userId", currentUserId.getId()); // 只查询当前登录用户的点赞信息
            queryWrapper.eq("isLiked", 1); // 只查询点赞状态为1的信息
            List<Picturelike> picturelikeList = picturelikeService.list(queryWrapper);
            userIdIsLikeMap = picturelikeList.stream()
                    .collect(Collectors.groupingBy(Picturelike::getPictureId));
        } else {
            userIdIsLikeMap = null;
        }

        // 2. 填充信息
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            Long pictureId = pictureVO.getId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setIsLiked(currentUserId != null && userIdIsLikeMap.containsKey(pictureId) ? 1 : 0);
            pictureVO.setUser(userService.getUserVO(user));

        });
        pictureVOPage.setRecords(pictureVOList);
        return pictureVOPage;
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        if (pictureQueryRequest == null) {
            return queryWrapper;
        }
        // 从对象中取值
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Date startEditTime = pictureQueryRequest.getStartEditTime();
        Date endEditTime = pictureQueryRequest.getEndEditTime();
        Long userId = pictureQueryRequest.getUserId();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String reviewMessage = pictureQueryRequest.getReviewMessage();
        Long reviewerId = pictureQueryRequest.getReviewerId();
        Long spaceId = pictureQueryRequest.getSpaceId();
        boolean nullSpaceId = pictureQueryRequest.isNullSpaceId();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();
        // 从多字段中搜索
        if (StrUtil.isNotBlank(searchText)) {
            // 需要拼接查询条件
            // and (name like "%xxx%" or introduction like "%xxx%")
            queryWrapper.and(
                    qw -> qw.like("name", searchText)
                            .or()
                            .like("introduction", searchText)
            );
        }
        queryWrapper.eq(ObjUtil.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjUtil.isNotEmpty(spaceId), "spaceId", spaceId);
        queryWrapper.isNull(nullSpaceId, "spaceId");
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        queryWrapper.like(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.like(StrUtil.isNotBlank(reviewMessage), "reviewMessage", reviewMessage);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(ObjUtil.isNotEmpty(picWidth), "picWidth", picWidth);
        queryWrapper.eq(ObjUtil.isNotEmpty(picHeight), "picHeight", picHeight);
        queryWrapper.eq(ObjUtil.isNotEmpty(picSize), "picSize", picSize);
        queryWrapper.eq(ObjUtil.isNotEmpty(picScale), "picScale", picScale);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewStatus), "reviewStatus", reviewStatus);
        queryWrapper.eq(ObjUtil.isNotEmpty(reviewerId), "reviewerId", reviewerId);
        // >= startEditTime
        queryWrapper.ge(ObjUtil.isNotEmpty(startEditTime), "editTime", startEditTime);
        // < endEditTime
        queryWrapper.lt(ObjUtil.isNotEmpty(endEditTime), "editTime", endEditTime);
        // JSON 数组查询
        if (CollUtil.isNotEmpty(tags)) {
            /* and (tag like "%\"Java\"%" and like "%\"Python\"%") */
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doPictureReview(PictureReviewRequest pictureReviewRequest, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(pictureReviewRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = pictureReviewRequest.getId();
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        PictureReviewStatusEnum reviewStatusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        String reviewMessage = pictureReviewRequest.getReviewMessage();
        if (id == null || reviewStatusEnum == null || PictureReviewStatusEnum.REVIEWING.equals(reviewStatusEnum)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 判断图片是否存在
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 3. 校验审核状态是否重复，已是改状态
        if (oldPicture.getReviewStatus().equals(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        // 4. 数据库操作
        Picture updatePicture = new Picture();
        BeanUtil.copyProperties(pictureReviewRequest, updatePicture);
        updatePicture.setReviewerId(loginUser.getId());
        updatePicture.setReviewTime(new Date());
        boolean result = this.updateById(updatePicture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 5. 同步更新 ES 数据
        try {
            // 先查询 ES 中是否存在该数据
            Optional<EsPicture> esOptional = esPictureDao.findById(id);
            EsPicture esPicture;
            if (esOptional.isPresent()) {
                // 如果存在，获取现有数据并更新审核相关字段
                esPicture = esOptional.get();
                esPicture.setReviewStatus(reviewStatus);
                esPicture.setReviewMessage(reviewMessage);
                esPicture.setReviewerId(loginUser.getId());
                esPicture.setReviewTime(updatePicture.getReviewTime());
            } else {
                // 如果不存在，从 MySQL 获取完整数据并创建新的 ES 文档
                Picture fullPicture = this.getById(id);
                esPicture = new EsPicture();
                BeanUtils.copyProperties(fullPicture, esPicture);
            }
            // 保存或更新到 ES
            esPictureDao.save(esPicture);
        } catch (Exception e) {
            log.error("Failed to sync picture review status to ES, pictureId: {}", id, e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "同步 ES 数据失败");
        }
    }

    /**
     * 填充审核参数
     *
     * @param picture
     * @param loginUser
     */
    @Override
    public void fillReviewParams(Picture picture, User loginUser) {
        if (userService.isAdmin(loginUser)) {
            // 管理员自动过审
            picture.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
            picture.setReviewerId(loginUser.getId());
            picture.setReviewMessage("管理员自动过审");
            picture.setReviewTime(new Date());
        } else {
            // 非管理员，无论是编辑还是创建默认都是待审核
            picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        }
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        // 校验参数
        String searchText = pictureUploadByBatchRequest.getSearchText();
        Integer count = pictureUploadByBatchRequest.getCount();
        ThrowUtils.throwIf(count > 30, ErrorCode.PARAMS_ERROR, "最多 30 条");
        // 名称前缀默认等于搜索关键词
        String namePrefix = pictureUploadByBatchRequest.getNamePrefix();
        if (StrUtil.isBlank(namePrefix)) {
            namePrefix = searchText;
        }
        // 抓取内容
        String fetchUrl = String.format("https://cn.bing.com/images/async?q=%s&mmasync=1", searchText);
        Document document;
        try {
            document = Jsoup.connect(fetchUrl).get();
        } catch (IOException e) {
            log.error("获取页面失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取页面失败");
        }
        // 解析内容
        Element div = document.getElementsByClass("dgControl").first();
        if (ObjUtil.isEmpty(div)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "获取元素失败");
        }
        Elements imgElementList = div.select("img.mimg");
        // 遍历元素，依次处理上传图片
        int uploadCount = 0;
        for (Element imgElement : imgElementList) {
            String fileUrl = imgElement.attr("src");
            if (StrUtil.isBlank(fileUrl)) {
                log.info("当前链接为空，已跳过：{}", fileUrl);
                continue;
            }
            // 处理图片的地址，防止转义或者和对象存储冲突的问题
            int questionMarkIndex = fileUrl.indexOf("?");
            if (questionMarkIndex > -1) {
                fileUrl = fileUrl.substring(0, questionMarkIndex);
            }
            // 上传图片
            PictureUploadRequest pictureUploadRequest = new PictureUploadRequest();
            pictureUploadRequest.setFileUrl(fileUrl);
            pictureUploadRequest.setPicName(namePrefix + (uploadCount + 1));
            pictureUploadRequest.setCategoryName(pictureUploadByBatchRequest.getCategoryName());
            pictureUploadRequest.setTagName(JSONUtil.toJsonStr(pictureUploadByBatchRequest.getTagName()));
            try {
                PictureVO pictureVO = this.uploadPicture(fileUrl, pictureUploadRequest, loginUser);
                log.info("图片上传成功，id = {}", pictureVO.getId());
                uploadCount++;
            } catch (Exception e) {
                log.error("图片上传失败", e);
                continue;
            }
            if (uploadCount >= count) {
                break;
            }
        }
        return uploadCount;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchOperationPicture(PictureOperation pictureOperation) {
        //获取批量操作类型
        long operationType = pictureOperation.getOperationType();
        //获取批量操作图片id
        List<Long> pictureIds = pictureOperation.getIds();
        boolean result = false;

        //批量删除
        if (operationType == OperationEnum.DELETE.getValue()) {
            //删除图片
            List<Picture> pictureList = listByIds(pictureIds);
            ThrowUtils.throwIf(pictureList == null || pictureList.isEmpty(), ErrorCode.NOT_FOUND_ERROR);

            result = Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                try {
                    // 批量删除MySQL数据
                    boolean deleteResult = removeByIds(pictureIds);
                    if (!deleteResult) {
                        return false;
                    }

                    // 批量删除ES数据
                    pictureIds.forEach(id -> {
                        try {
                            esPictureDao.deleteById(id);
                        } catch (Exception e) {
                            log.error("Delete picture from ES failed, pictureId: {}", id, e);
                        }
                    });

                    // 删除图片文件
                    for (Picture oldPicture : pictureList) {
                        this.clearPictureFile(oldPicture);
                    }

                    return true;
                } catch (Exception e) {
                    status.setRollbackOnly();
                    throw e;
                }
            }));
        }
        //批量通过或不通过
        else if (operationType == OperationEnum.APPROVE.getValue() ||
                operationType == OperationEnum.REJECT.getValue()) {
            try {
                // 设置审核状态
                Integer reviewStatus = operationType == OperationEnum.APPROVE.getValue() ?
                        PictureReviewStatusEnum.PASS.getValue() :
                        PictureReviewStatusEnum.REJECT.getValue();

                // 更新 MySQL 数据
                result = update()
                        .set("reviewStatus", reviewStatus)
                        .set("reviewTime", new Date())
                        .set("reviewMessage", operationType == OperationEnum.APPROVE.getValue() ?
                                "批量审核通过" : "批量审核不通过")
                        .in("id", pictureIds)
                        .update();

                if (result) {
                    // 批量更新 ES 数据
                    List<EsPicture> esPictures = new ArrayList<>();
                    for (Long pictureId : pictureIds) {
                        Optional<EsPicture> esOptional = esPictureDao.findById(pictureId);
                        EsPicture esPicture;
                        if (esOptional.isPresent()) {
                            // 如果存在，只更新审核状态
                            esPicture = esOptional.get();
                            esPicture.setReviewStatus(reviewStatus);
                            esPicture.setReviewTime(new Date());
                            esPicture.setReviewMessage(operationType == OperationEnum.APPROVE.getValue() ?
                                    "批量审核通过" : "批量审核不通过");
                        } else {
                            // 如果不存在，从 MySQL 获取完整数据
                            Picture picture = this.getById(pictureId);
                            if (picture != null) {
                                esPicture = new EsPicture();
                                BeanUtils.copyProperties(picture, esPicture);
                                esPicture.setReviewStatus(reviewStatus);
                                esPicture.setReviewTime(new Date());
                                esPicture.setReviewMessage(operationType == OperationEnum.APPROVE.getValue() ?
                                        "批量审核通过" : "批量审核不通过");
                            } else {
                                continue;
                            }
                        }
                        esPictures.add(esPicture);
                    }

                    if (!esPictures.isEmpty()) {
                        // 批量保存到 ES
                        esPictureDao.saveAll(esPictures);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to sync pictures review status to ES, pictureIds: {}, operationType: {}",
                        pictureIds, operationType, e);
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "同步 ES 数据失败");
            }
        }

        return result;
    }

    @Async
    @Override
    public void clearPictureFile(Picture oldPicture) {
        if (oldPicture == null) {
            // 若 oldPicture 为 null，直接返回，避免空指针异常
            return;
        }
        // 判断该图片是否被多条记录使用
        String pictureUrl = oldPicture.getUrl();
        long count = this.lambdaQuery()
                .eq(Picture::getUrl, pictureUrl)
                .count();
        // 有不止一条记录用到了该图片，不清理
        if (count > 1) {
            return;
        }
        // 删除图片
        cosManager.deleteObject(pictureUrl);
        // 删除缩略图
        String thumbnailUrl = oldPicture.getThumbnailUrl();
        if (StrUtil.isNotBlank(thumbnailUrl)) {
            cosManager.deleteObject(thumbnailUrl);
        }
    }

    @Override
    public void checkPictureAuth(User loginUser, Picture picture) {
        Long spaceId = picture.getSpaceId();
        Long loginUserId = loginUser.getId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!picture.getUserId().equals(loginUserId) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!picture.getUserId().equals(loginUserId)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    @Override
    public void deletePicture(long pictureId, User loginUser) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 判断是否存在
        Picture oldPicture = this.getById(pictureId);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
//        checkPictureAuth(loginUser, oldPicture);
        // 开启事务
        Long finalSpaceId = oldPicture.getSpaceId();
        transactionTemplate.execute(status -> {
            try {
                // 操作数据库
                boolean result = this.removeById(pictureId);
                ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

                // 更新空间的使用额度，释放额度
                if(finalSpaceId != null){
                    boolean update = spaceService.lambdaUpdate()
                            .eq(Space::getId, oldPicture.getSpaceId())
                            .setSql("totalSize = totalSize - " + oldPicture.getPicSize())
                            .setSql("totalCount = totalCount - 1")
                            .update();
                    ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "额度更新失败");
                }

                // 从ES删除
                try {
                    esPictureDao.deleteById(pictureId);
                } catch (Exception e) {
                    log.error("Delete picture from ES failed, pictureId: {}", pictureId, e);
                    // 这里可以选择是否抛出异常，取决于业务需求
                    // throw new BusinessException(ErrorCode.OPERATION_ERROR, "ES删除失败");
                }

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw e;
            }
        });
        // 异步清理文件
        this.clearPictureFile(oldPicture);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        // 在此处将实体类和 DTO 进行转换
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureEditRequest, picture);
        // 注意将 list 转为 string
        picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        // 设置编辑时间
        picture.setEditTime(new Date());
        // 数据校验
        this.validPicture(picture);
        // 判断是否存在
        long id = pictureEditRequest.getId();
        Picture oldPicture = this.getById(id);
        ThrowUtils.throwIf(oldPicture == null, ErrorCode.NOT_FOUND_ERROR);

        // 保留原有数据中的一些字段
        picture.setUrl(oldPicture.getUrl());
        picture.setThumbnailUrl(oldPicture.getThumbnailUrl());
        picture.setPicSize(oldPicture.getPicSize());
        picture.setPicWidth(oldPicture.getPicWidth());
        picture.setPicHeight(oldPicture.getPicHeight());
        picture.setPicScale(oldPicture.getPicScale());
        picture.setPicFormat(oldPicture.getPicFormat());
        picture.setPicColor(oldPicture.getPicColor());
        picture.setUserId(oldPicture.getUserId());
        picture.setSpaceId(oldPicture.getSpaceId());
        picture.setCreateTime(oldPicture.getCreateTime());

        // 补充审核参数
        this.fillReviewParams(picture, loginUser);
        // 操作数据库
        boolean result = this.updateById(picture);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);

        // 同步更新 ES 数据
        try {
            // 先查询 ES 中是否存在该数据
            Optional<EsPicture> esOptional = esPictureDao.findById(id);
            EsPicture esPicture;
            if (esOptional.isPresent()) {
                // 如果存在，获取现有数据
                esPicture = esOptional.get();
                // 只更新需要修改的字段
                esPicture.setName(picture.getName());
                esPicture.setIntroduction(picture.getIntroduction());
                esPicture.setCategory(picture.getCategory());
                esPicture.setTags(picture.getTags());
                esPicture.setEditTime(picture.getEditTime());
                esPicture.setReviewStatus(picture.getReviewStatus());
                esPicture.setReviewMessage(picture.getReviewMessage());
            } else {
                // 如果不存在，创建新的 ES 文档
                esPicture = new EsPicture();
                BeanUtils.copyProperties(picture, esPicture);
            }
            // 保存或更新到 ES
            esPictureDao.save(esPicture);
        } catch (Exception e) {
            log.error("Failed to sync picture to ES during edit, pictureId: {}", picture.getId(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "同步 ES 数据失败");
        }
    }

    @Override
    public List<PictureVO> searchPictureByColor(Long spaceId, String picColor, User loginUser) {
        // 1. 校验参数
        ThrowUtils.throwIf(spaceId == null || StrUtil.isBlank(picColor), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);
        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }
        // 3. 查询该空间下的所有图片（必须要有主色调）
        List<Picture> pictureList = this.lambdaQuery()
                .eq(Picture::getSpaceId, spaceId)
                .isNotNull(Picture::getPicColor)
                .list();
        // 如果没有图片，直接返回空列表
        if (CollUtil.isEmpty(pictureList)) {
            return new ArrayList<>();
        }
        // 将颜色字符串转换为主色调
        Color targetColor = Color.decode(picColor);
        // 4. 计算相似度并排序
        List<Picture> sortedPictureList = pictureList.stream()
                .sorted(Comparator.comparingDouble(picture -> {
                    String hexColor = picture.getPicColor();
                    // 没有主色调的图片会默认排序到最后
                    if (StrUtil.isBlank(hexColor)) {
                        return Double.MAX_VALUE;
                    }
                    Color pictureColor = Color.decode(hexColor);
                    // 计算相似度
                    // 越大越相似
                    return -ColorSimilarUtils.calculateSimilarity(targetColor, pictureColor);
                }))
                .limit(12) // 取前 12 个
                .collect(Collectors.toList());
        // 5. 返回结果
        return sortedPictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void editPictureByBatch(PictureEditByBatchRequest pictureEditByBatchRequest, User loginUser) {
        // 1. 获取和校验参数
        List<Long> pictureIdList = pictureEditByBatchRequest.getPictureIdList();
        Long spaceId = pictureEditByBatchRequest.getSpaceId();
        String category = pictureEditByBatchRequest.getCategory();
        List<String> tags = pictureEditByBatchRequest.getTags();
        ThrowUtils.throwIf(CollUtil.isEmpty(pictureIdList), ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(spaceId == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NO_AUTH_ERROR);

        // 2. 校验空间权限
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!space.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        }

        // 3. 查询指定图片（仅选择需要的字段）
        List<Picture> pictureList = this.lambdaQuery()
                .select(Picture::getId, Picture::getSpaceId)
                .eq(Picture::getSpaceId, spaceId)
                .in(Picture::getId, pictureIdList)
                .list();
        if (pictureList.isEmpty()) {
            return;
        }

        // 4. 更新分类和标签
        pictureList.forEach(picture -> {
            if (StrUtil.isNotBlank(category)) {
                picture.setCategory(category);
            }
            if (CollUtil.isNotEmpty(tags)) {
                picture.setTags(JSONUtil.toJsonStr(tags));
            }
        });

        // 批量重命名
        String nameRule = pictureEditByBatchRequest.getNameRule();
        fillPictureWithNameRule(pictureList, nameRule);

        // 5. 操作数据库进行批量更新
        boolean result = this.updateBatchById(pictureList);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "批量编辑失败");

        // 6. 同步更新 ES 数据
        try {
            List<EsPicture> esPictures = new ArrayList<>();
            for (Picture picture : pictureList) {
                Long pictureId = picture.getId();
                Optional<EsPicture> esOptional = esPictureDao.findById(pictureId);
                EsPicture esPicture;
                if (esOptional.isPresent()) {
                    esPicture = esOptional.get();
                    if (StrUtil.isNotBlank(nameRule)) {
                        esPicture.setName(picture.getName());
                    }
                    if (StrUtil.isNotBlank(category)) {
                        esPicture.setCategory(category);
                    }
                    if (CollUtil.isNotEmpty(tags)) {
                        esPicture.setTags(JSONUtil.toJsonStr(tags));
                    }
                } else {
                    Picture fullPicture = this.getById(pictureId);
                    esPicture = new EsPicture();
                    BeanUtils.copyProperties(fullPicture, esPicture);
                }
                esPictures.add(esPicture);
            }
            // 批量保存到 ES
            esPictureDao.saveAll(esPictures);
        } catch (Exception e) {
            log.error("Failed to sync pictures to ES during batch edit, pictureIds: {}",
                    pictureIdList.toString(), e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "同步 ES 数据失败");
        }
    }

    @Override
    public CreateOutPaintingTaskResponse createPictureOutPaintingTask(CreatePictureOutPaintingTaskRequest createPictureOutPaintingTaskRequest, User loginUser) {
        // 获取图片信息
        Long pictureId = createPictureOutPaintingTaskRequest.getPictureId();
        Picture picture = Optional.ofNullable(this.getById(pictureId))
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在"));
        // 校验权限
        // checkPictureAuth(loginUser, picture);
        // 创建扩图任务
        CreateOutPaintingTaskRequest createOutPaintingTaskRequest = new CreateOutPaintingTaskRequest();
        CreateOutPaintingTaskRequest.Input input = new CreateOutPaintingTaskRequest.Input();
        input.setImageUrl(picture.getUrl());
        createOutPaintingTaskRequest.setInput(input);
        createOutPaintingTaskRequest.setParameters(createPictureOutPaintingTaskRequest.getParameters());
        // 创建任务
        return aliYunAiApi.createOutPaintingTask(createOutPaintingTaskRequest);
    }


    @Override
    public void crawlerDetect(HttpServletRequest request) {
        // 调用多少次时告警
        final int WARN_COUNT = CrawlerConstant.WARN_COUNT;
        // 调用多少次时封号
        final int BAN_COUNT = CrawlerConstant.BAN_COUNT;
        User loginUser = null;
        String key;
        String identifier; // 可以是用户 ID 或 IP 地址
        try {
            loginUser = userService.getLoginUser(request);
            if (loginUser!= null) {
                // 获取用户 id
                Long loginUserId = loginUser.getId();
                identifier = String.valueOf(loginUserId);
                // 拼接访问 key
                key = String.format("user:access:%s", loginUserId);
            } else {
                // 获取用户 IP 地址
                identifier = getClientIpAddress(request);
                // 拼接访问 key
                key = String.format("ip:access:%s", identifier);
            }
        } catch (Exception e) {
            log.info("获取用户信息异常，默认为未登录用户");
            // 获取用户 IP 地址
            identifier = getClientIpAddress(request);
            key = String.format("ip:access:%s", identifier);
        }
        // 统计一分钟内访问次数，180 秒过期
        long count = counterManager.incrAndGetCounter(key, 1, TimeUnit.MINUTES, CrawlerConstant.EXPIRE_TIME);
        // 是否封禁或告警
        if (count > BAN_COUNT) {
            if (loginUser!= null) {
                // 对于登录用户，踢下线和封号
                StpUtil.kickout(loginUser.getId());
                // 封号
                User updateUser = new User();
                updateUser.setId(loginUser.getId());
                updateUser.setUserRole("ban");
                userService.updateById(updateUser);
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问次数过多，已被封号");
            } else {
                // 对于未登录用户，封禁 IP
                banIp(identifier);
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "访问次数过多，IP 已被封禁");
            }
        } else if (count == WARN_COUNT) {
            // 可以改为向管理员发送邮件通知
            throw new BusinessException(110, "警告：访问太频繁,请勿恶意频繁访问，否则将被封号处理");
        }
    }

    @Override
    public List<PictureVO> getTop100Picture(Long id) {
        List<Picture> pictureList = null;
        if (id == 0) {
            // 最近一年
            pictureList = pictureMapper.getTop100PictureByYear();
        } else if (id == 1) {
            // 最近一月
            pictureList = pictureMapper.getTop100PictureByMonth();
        } else {
            // 最近一周
            pictureList = pictureMapper.getTop100PictureByWeek();
        }
        // 转换为 VO 对象
        return pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());
    }

    @Transactional
    @Override
    public Page<PictureVO> getFollowPicture(HttpServletRequest request, PictureQueryRequest pictureQueryRequest) {
        long current = pictureQueryRequest.getCurrent();
        long size = pictureQueryRequest.getPageSize();
        Page<Picture> page = new Page<>(current, size);

        // 查询是否登录
        User currentUser = userService.getLoginUser(request);

        // 处理用户未登录的情况
        if (currentUser == null) {
            return new Page<>();
        }

        // 获取用户 id
        Long id = currentUser.getId();

        // 获取关注列表
        List<Long> followList = userfollowsService.getFollowList(id);

        // 确保 followList 不为空且不包含 null 元素
        followList = followList.stream()
                .filter(item -> item!= null)
                .collect(Collectors.toList());

        if (followList.isEmpty()) {
            return new Page<>();
        }

        // 创建 QueryWrapper 筛选出 userId 在关注列表中的图片
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("userId", followList);
        queryWrapper.isNull("spaceId").or().eq("spaceId", 0);
        // 可以添加排序逻辑，例如按照创建时间降序排序
        queryWrapper.orderByDesc("createTime");

        // 获取图片列表
        Page<Picture> picturePage = this.page(page, queryWrapper);
        List<Picture> pictureList = picturePage.getRecords();

        // 将 Picture 列表转换为 PictureVO 列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(PictureVO::objToVo)
                .collect(Collectors.toList());

        // 关联查询用户信息
        Map<Long, User> userIdUserMap = getUserMap(pictureList);

        // 查询是否点赞
        Map<Long, Boolean> pictureIdIsLikedMap = getPictureIdIsLikedMap(currentUser, pictureList);

        // 填充用户信息和点赞信息
        fillPictureVOInfo(pictureVOList, userIdUserMap, pictureIdIsLikedMap);

        Page<PictureVO> pictureVOPage = new Page<>(current, size, picturePage.getTotal());
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;
    }


    private Map<Long, User> getUserMap(List<Picture> pictureList) {
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .collect(Collectors.toSet());


        // 检查 userIdSet 是否为空
        if (userIdSet.isEmpty()) {
            return null;
        }


        return userService.listByIds(userIdSet)
                .stream()
                .collect(Collectors.toMap(User::getId, user -> user, (u1, u2) -> u1));
    }


    private Map<Long, Boolean> getPictureIdIsLikedMap(User currentUser, List<Picture> pictureList) {
        if (pictureList.isEmpty()) {
            return null;
        }


        Set<Long> pictureIdSet = pictureList.stream()
                .map(Picture::getId)
                .collect(Collectors.toSet());


        QueryWrapper<Picturelike> likeQueryWrapper = new QueryWrapper<>();
        likeQueryWrapper.in("pictureId", pictureIdSet);
        likeQueryWrapper.eq("userId", currentUser.getId());
        likeQueryWrapper.eq("isLiked", 1);


        List<Picturelike> picturelikeList = picturelikeService.list(likeQueryWrapper);


        return picturelikeList.stream()
                .collect(Collectors.toMap(Picturelike::getPictureId, like -> true, (b1, b2) -> b1));
    }


    private void fillPictureVOInfo(List<PictureVO> pictureVOList, Map<Long, User> userIdUserMap, Map<Long, Boolean> pictureIdIsLikedMap) {
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            Long pictureId = pictureVO.getId();
            User user = userIdUserMap.get(userId);
            pictureVO.setUser(userService.getUserVO(user));


            if (pictureIdIsLikedMap!= null) {
                pictureVO.setIsLiked(pictureIdIsLikedMap.getOrDefault(pictureId, false)? 1 : 0);
            } else {
                pictureVO.setIsLiked(0);
            }
        });
    }
    // 获取客户端 IP 地址的方法
    private String getClientIpAddress(HttpServletRequest request) {
        String remoteAddr = "";
        if (request!= null) {
            remoteAddr = request.getHeader("X-FORWARDED-FOR");
            if (remoteAddr == null || "".equals(remoteAddr)) {
                remoteAddr = request.getRemoteAddr();
            }
        }
        return remoteAddr;
    }

    // 封禁 IP 的方法，这里可以根据实际情况实现，比如将 IP 加入 Redis 黑名单等
    private void banIp(String ip) {
        // 假设使用 Redis 存储封禁的 IP 列表
        stringRedisTemplate.opsForValue().set("ban:ip:" + ip, "true", 3600, TimeUnit.SECONDS);
    }

    // 检查 IP 是否被封禁的方法，可以在需要的地方调用
    private boolean isIpBanned(String ip) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().get("ban:ip:" + ip));
    }

    /**
     * nameRule 格式：图片{序号}
     *
     * @param pictureList
     * @param nameRule
     */
    private void fillPictureWithNameRule(List<Picture> pictureList, String nameRule) {
        if (StrUtil.isBlank(nameRule) || CollUtil.isEmpty(pictureList)) {
            return;
        }
        long count = 1;
        try {
            for (Picture picture : pictureList) {
                String pictureName = nameRule.replaceAll("\\{序号}", String.valueOf(count++));
                picture.setName(pictureName);
            }
        } catch (Exception e) {
            log.error("名称解析错误", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "名称解析错误");
        }
    }
}




