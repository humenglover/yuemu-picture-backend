package com.lumenglover.yuemupicturebackend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lumenglover.yuemupicturebackend.mapper.PexelsCrawlRecordMapper;
import com.lumenglover.yuemupicturebackend.model.entity.PexelsCrawlRecord;
import com.lumenglover.yuemupicturebackend.service.PexelsCrawlRecordService;
import org.springframework.stereotype.Service;

/**
 * Pexels 抓取记录服务实现
 */
@Service
public class PexelsCrawlRecordServiceImpl extends ServiceImpl<PexelsCrawlRecordMapper, PexelsCrawlRecord>
        implements PexelsCrawlRecordService {

    @Override
    public boolean existsByPexelsPhotoId(Long pexelsPhotoId) {
        QueryWrapper<PexelsCrawlRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("pexelsPhotoId", pexelsPhotoId);
        return this.count(queryWrapper) > 0;
    }
}
