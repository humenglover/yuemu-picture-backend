package com.lumenglover.yuemupicturebackend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumenglover.yuemupicturebackend.model.entity.Picture;
import com.lumenglover.yuemupicturebackend.model.vo.PictureVO;

import java.util.List;

/**
* @author 鹿梦
* @description 针对表【picture(图片)】的数据库操作Mapper
* @createDate 2024-12-11 20:45:51
* @Entity com.lumenglover.yuemupicturebackend.model.entity.Picture
*/
public interface PictureMapper extends BaseMapper<Picture> {

    List<Picture> getTop100PictureByYear();

    List<Picture> getTop100PictureByMonth();

    List<Picture> getTop100PictureByWeek();
}




