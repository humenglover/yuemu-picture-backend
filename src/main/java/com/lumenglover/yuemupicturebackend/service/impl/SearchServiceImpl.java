package com.lumenglover.yuemupicturebackend.service.impl;

import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.model.dto.search.SearchRequest;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.lumenglover.yuemupicturebackend.model.vo.PictureVO;
import com.lumenglover.yuemupicturebackend.model.vo.UserVO;
import com.lumenglover.yuemupicturebackend.service.SearchService;
import com.lumenglover.yuemupicturebackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    private static final String PICTURE_INDEX = "picture";
    private static final String USER_INDEX = "user";

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private UserService userService;

    @Override
    public Page<?> doSearch(SearchRequest searchRequest) {
        String searchText = searchRequest.getSearchText();
        String type = searchRequest.getType();

        // 校验参数
        if (StringUtils.isBlank(searchText)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索关键词不能为空");
        }

        // 根据type选择不同的搜索策略
        switch (type) {
            case "picture":
                return searchPicture(searchRequest);
            case "user":
                return searchUser(searchRequest);
            default:
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的搜索类型");
        }
    }

    /**
     * 搜索图片
     */
    private Page<PictureVO> searchPicture(SearchRequest searchRequest) {
        String searchText = searchRequest.getSearchText();
        Integer current = searchRequest.getCurrent();
        Integer pageSize = searchRequest.getPageSize();

        // 构建布尔查询
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                // 搜索条件
                .should(QueryBuilders.matchQuery("name", searchText))
                .should(QueryBuilders.matchQuery("introduction", searchText))
                .should(QueryBuilders.matchQuery("tags", searchText));

        // 尝试将搜索文本转换为图片ID
        try {
            Long pictureId = Long.parseLong(searchText);
            boolQueryBuilder.should(QueryBuilders.termQuery("id", pictureId));
        } catch (NumberFormatException ignored) {
        }

        boolQueryBuilder.minimumShouldMatch(1)
                // 必要条件：已通过审核、未删除、公共图库
                .must(QueryBuilders.termQuery("reviewStatus", 1))
                .must(QueryBuilders.termQuery("isDelete", 0))
                .must(QueryBuilders.boolQuery()
                        .should(QueryBuilders.boolQuery()
                                .mustNot(QueryBuilders.existsQuery("spaceId")))
                        .should(QueryBuilders.termQuery("spaceId", 0))
                );

        // 构建搜索查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(PageRequest.of(current - 1, pageSize))
                .withSort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .withSort(SortBuilders.fieldSort("createTime").order(SortOrder.DESC))
                .build();

        // 执行搜索
        SearchHits<Picture> searchHits = elasticsearchRestTemplate.search(
                searchQuery,
                Picture.class,
                IndexCoordinates.of(PICTURE_INDEX)
        );

        // 获取搜索结果并转换为PictureVO
        List<PictureVO> pictureVOList = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(picture -> {
                    PictureVO pictureVO = PictureVO.objToVo(picture);
                    // 获取并设置脱敏后的用户信息
                    User user = userService.getById(picture.getUserId());
                    if (user != null) {
                        pictureVO.setUser(userService.getUserVO(user));
                    }
                    return pictureVO;
                })
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                pictureVOList,
                PageRequest.of(current - 1, pageSize),
                searchHits.getTotalHits()
        );
    }

    /**
     * 搜索用户
     */
    private Page<UserVO> searchUser(SearchRequest searchRequest) {
        String searchText = searchRequest.getSearchText();
        Integer current = searchRequest.getCurrent();
        Integer pageSize = searchRequest.getPageSize();

        // 构建布尔查询
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery("userName", searchText))
                .should(QueryBuilders.matchQuery("userAccount", searchText))
                .should(QueryBuilders.matchQuery("userProfile", searchText));

        // 尝试将搜索文本转换为用户ID
        try {
            Long userId = Long.parseLong(searchText);
            boolQueryBuilder.should(QueryBuilders.termQuery("id", userId));
        } catch (NumberFormatException ignored) {
        }

        boolQueryBuilder.minimumShouldMatch(1)
                .must(QueryBuilders.termQuery("isDelete", 0));

        // 构建搜索查询
        NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                .withQuery(boolQueryBuilder)
                .withPageable(PageRequest.of(current - 1, pageSize))
                .withSort(SortBuilders.scoreSort().order(SortOrder.DESC))
                .withSort(SortBuilders.fieldSort("createTime").order(SortOrder.DESC))
                .build();

        // 执行搜索
        SearchHits<User> searchHits = elasticsearchRestTemplate.search(
                searchQuery,
                User.class,
                IndexCoordinates.of(USER_INDEX)
        );

        // 获取搜索结果并转换为UserVO
        List<UserVO> userVOList = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(userService::getUserVO)  // 使用UserService的脱敏方法
                .collect(Collectors.toList());

        return new org.springframework.data.domain.PageImpl<>(
                userVOList,
                PageRequest.of(current - 1, pageSize),
                searchHits.getTotalHits()
        );
    }
}
