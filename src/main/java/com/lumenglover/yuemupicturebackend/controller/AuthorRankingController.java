package com.lumenglover.yuemupicturebackend.controller;

import com.lumenglover.yuemupicturebackend.annotation.RateLimiter;
import com.lumenglover.yuemupicturebackend.common.BaseResponse;
import com.lumenglover.yuemupicturebackend.common.ResultUtils;
import com.lumenglover.yuemupicturebackend.exception.BusinessException;
import com.lumenglover.yuemupicturebackend.exception.ErrorCode;
import com.lumenglover.yuemupicturebackend.model.enums.AuthorRankingTypeEnum;
import com.lumenglover.yuemupicturebackend.model.enums.TimeRangeEnum;
import com.lumenglover.yuemupicturebackend.model.vo.AuthorRankingVO;
import com.lumenglover.yuemupicturebackend.service.AuthorRankingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 作者榜单控制器
 */
@RestController
@RequestMapping("/api/author-ranking")
@Slf4j
public class AuthorRankingController {

    @Resource
    private AuthorRankingService authorRankingService;

    /**
     * 获取作者榜单
     * @param rankingType 榜单类型：picture-图片作者榜, post-帖子作者榜
     * @param timeRange 时间范围：day-日榜, week-周榜, month-月榜, total-总榜
     * @param limit 返回数量限制（默认100，最大100）
     */
    @GetMapping("/list")
    @RateLimiter(key = "author_ranking_list", time = 60, count = 30, message = "作者榜单查询过于频繁，请稍后再试")
    public BaseResponse<List<AuthorRankingVO>> getAuthorRankingList(
            @RequestParam String rankingType,
            @RequestParam String timeRange,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        // 参数校验
        AuthorRankingTypeEnum typeEnum = AuthorRankingTypeEnum.getEnumByValue(rankingType);
        if (typeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的榜单类型");
        }

        TimeRangeEnum rangeEnum = TimeRangeEnum.getEnumByValue(timeRange);
        if (rangeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的时间范围");
        }

        List<AuthorRankingVO> rankingList = authorRankingService.getAuthorRankingList(
                rankingType, timeRange, limit
        );

        return ResultUtils.success(rankingList);
    }

    /**
     * 获取图片作者榜单
     * @param timeRange 时间范围：day-日榜, week-周榜, month-月榜, total-总榜
     */
    @GetMapping("/picture/{timeRange}")
    @RateLimiter(key = "picture_author_ranking", time = 60, count = 30, message = "图片作者榜单查询过于频繁，请稍后再试")
    public BaseResponse<List<AuthorRankingVO>> getPictureAuthorRanking(
            @PathVariable String timeRange,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        TimeRangeEnum rangeEnum = TimeRangeEnum.getEnumByValue(timeRange);
        if (rangeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的时间范围");
        }

        List<AuthorRankingVO> rankingList = authorRankingService.getAuthorRankingList(
                AuthorRankingTypeEnum.PICTURE.getValue(), timeRange, limit
        );

        return ResultUtils.success(rankingList);
    }

    /**
     * 获取帖子作者榜单
     * @param timeRange 时间范围：day-日榜, week-周榜, month-月榜, total-总榜
     */
    @GetMapping("/post/{timeRange}")
    @RateLimiter(key = "post_author_ranking", time = 60, count = 30, message = "帖子作者榜单查询过于频繁，请稍后再试")
    public BaseResponse<List<AuthorRankingVO>> getPostAuthorRanking(
            @PathVariable String timeRange,
            @RequestParam(required = false, defaultValue = "100") Integer limit
    ) {
        TimeRangeEnum rangeEnum = TimeRangeEnum.getEnumByValue(timeRange);
        if (rangeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "无效的时间范围");
        }

        List<AuthorRankingVO> rankingList = authorRankingService.getAuthorRankingList(
                AuthorRankingTypeEnum.POST.getValue(), timeRange, limit
        );

        return ResultUtils.success(rankingList);
    }
}
