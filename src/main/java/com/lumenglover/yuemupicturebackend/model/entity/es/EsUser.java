package com.lumenglover.yuemupicturebackend.model.entity.es;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

@Document(indexName = "user")
@Data
public class EsUser {
    /**
     * id
     */
    @Id
    private Long id;

    /**
     * 账号
     */
    @Field(type = FieldType.Keyword)
    private String userAccount;

    /**
     * 用户昵称
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String userName;

    /**
     * 用户头像
     */
    @Field(type = FieldType.Keyword)
    private String userAvatar;

    /**
     * 用户简介
     */
    @Field(type = FieldType.Text, analyzer = "ik_max_word", searchAnalyzer = "ik_smart")
    private String userProfile;

    /**
     * 用户角色：user/admin
     */
    @Field(type = FieldType.Keyword)
    private String userRole;

    /**
     * 编辑时间
     */
    @Field(type = FieldType.Date)
    private Date editTime;

    /**
     * 创建时间
     */
    @Field(type = FieldType.Date)
    private Date createTime;

    /**
     * 更新时间
     */
    @Field(type = FieldType.Date)
    private Date updateTime;

    /**
     * 是否删除
     */
    @Field(type = FieldType.Integer)
    private Integer isDelete;
} 