package com.lumenglover.yuemupicturebackend.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间存储额度枚举类
 */
public enum SpaceStorageQuotaEnum {

    NORMAL("普通用户", 0, 50),
    PRO("Pro会员", 1, 200),
    PLUS("Plus会员/管理员", 2, 500);

    private final String text;
    private final int value;
    private final int maxStorage; // 单位: MB

    SpaceStorageQuotaEnum(String text, int value, int maxStorage) {
        this.text = text;
        this.value = value;
        this.maxStorage = maxStorage;
    }

    /**
     * 获取值列表
     *
     * @return
     */
    public static List<Integer> getValues() {
        return Arrays.stream(values()).map(item -> item.value).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static SpaceStorageQuotaEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (SpaceStorageQuotaEnum anEnum : SpaceStorageQuotaEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }

    public String getText() {
        return text;
    }

    public int getValue() {
        return value;
    }

    public int getMaxStorage() {
        return maxStorage;
    }
}
