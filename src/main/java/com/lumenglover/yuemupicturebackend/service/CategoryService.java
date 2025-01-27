package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.entity.Category;
import com.lumenglover.yuemupicturebackend.model.entity.Tag;
import com.lumenglover.yuemupicturebackend.model.vo.CategoryVO;
import com.lumenglover.yuemupicturebackend.model.vo.TagVO;

import java.util.List;

/**
* @author 鹿梦
* @description 针对表【category(分类)】的数据库操作Service
* @createDate 2024-12-13 17:37:23
*/
public interface CategoryService extends IService<Category> {

    List<String> listCategory();

    List<CategoryVO> listCategoryVO(List<Category> records);

    CategoryVO getCategoryVO(Category category);

    List<CategoryVO> findCategory(String categoryName);
}
