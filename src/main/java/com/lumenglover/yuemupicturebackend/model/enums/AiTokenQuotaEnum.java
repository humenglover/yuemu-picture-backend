package com.lumenglover.yuemupicturebackend.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI Token 额度枚举类
 */
public enum AiTokenQuotaEnum {

    NORMAL("普通用户", 0, 80000L, 300000L, 5L, 100L),
    PRO("Pro会员", 1, 180000L, 700000L, 15L, 500L),
    PLUS("Plus会员/管理员", 2, 400000L, 1500000L, 30L, 2000L);

    private final String text;
    private final int value;
    private final long limit5h;
    private final long limitWeek;
    private final long limitImageGenWeek;
    private final long limitImageSearchWeek;

    AiTokenQuotaEnum(String text, int value, long limit5h, long limitWeek, long limitImageGenWeek, long limitImageSearchWeek) {
        this.text = text;
        this.value = value;
        this.limit5h = limit5h;
        this.limitWeek = limitWeek;
        this.limitImageGenWeek = limitImageGenWeek;
        this.limitImageSearchWeek = limitImageSearchWeek;
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
    public static AiTokenQuotaEnum getEnumByValue(Integer value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (AiTokenQuotaEnum anEnum : AiTokenQuotaEnum.values()) {
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

    public long getLimit5h() {
        return limit5h;
    }

    public long getLimitWeek() {
        return limitWeek;
    }

    public long getLimitImageGenWeek() {
        return limitImageGenWeek;
    }

    public long getLimitImageSearchWeek() {
        return limitImageSearchWeek;
    }
}
