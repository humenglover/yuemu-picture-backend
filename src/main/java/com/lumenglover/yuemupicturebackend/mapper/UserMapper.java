package com.lumenglover.yuemupicturebackend.mapper;

import com.lumenglover.yuemupicturebackend.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;

/**
* @author 鹿梦
* @description 针对表【user(用户)】的数据库操作Mapper
* @createDate 2024-12-10 10:39:52
* @Entity generator.domain.User
*/
public interface UserMapper extends BaseMapper<User> {

    @Select("select count(*) from user where userAccount = #{userAccount}")
    long selectByAccount(String userAccount);
}




