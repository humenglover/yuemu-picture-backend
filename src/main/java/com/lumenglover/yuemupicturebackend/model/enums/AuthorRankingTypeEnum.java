package com.lumenglover.yuemupicturebackend.model.enums;

import lombok.Getter;

/**
 * 作者榜单类型枚举
 */
@Getter
public enum AuthorRankingTypeEnum {

    PICTURE("picture", "图片作者榜"),
    POST("post", "帖子作者榜");

    private final String value;
    private final String text;

    AuthorRankingTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据值获取枚举
     */
    public static AuthorRankingTypeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (AuthorRankingTypeEnum typeEnum : AuthorRankingTypeEnum.values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}
