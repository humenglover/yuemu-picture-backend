package com.lumenglover.yuemupicturebackend.model.enums;

import lombok.Getter;

/**
 * 时间范围枚举
 */
@Getter
public enum TimeRangeEnum {

    DAY("day", "日榜", 1),
    WEEK("week", "周榜", 7),
    MONTH("month", "月榜", 30),
    TOTAL("total", "总榜", -1);

    private final String value;
    private final String text;
    private final int days;

    TimeRangeEnum(String value, String text, int days) {
        this.value = value;
        this.text = text;
        this.days = days;
    }

    /**
     * 根据值获取枚举
     */
    public static TimeRangeEnum getEnumByValue(String value) {
        if (value == null) {
            return null;
        }
        for (TimeRangeEnum rangeEnum : TimeRangeEnum.values()) {
            if (rangeEnum.value.equals(value)) {
                return rangeEnum;
            }
        }
        return null;
    }
}
