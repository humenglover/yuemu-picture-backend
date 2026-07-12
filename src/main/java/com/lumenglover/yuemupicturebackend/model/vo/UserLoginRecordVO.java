package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户登录记录视图对象
 */
@Data
public class UserLoginRecordVO implements Serializable {

    /**
     * 主键
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 登录时间
     */
    private Date loginTime;

    /**
     * 登录IP地址
     */
    private String loginIp;

    /**
     * 登录地点（省份-城市）
     */
    private String loginLocation;

    /**
     * 设备类型：PC/Mobile/Tablet/Unknown
     */
    private String deviceType;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 操作系统类型：Windows/MacOS/Linux/iOS/Android/Unknown
     */
    private String osType;

    /**
     * 操作系统版本
     */
    private String osVersion;

    /**
     * 浏览器类型：Chrome/Firefox/Safari/Edge/Unknown
     */
    private String browserType;

    /**
     * 浏览器版本
     */
    private String browserVersion;

    /**
     * 登录状态：0-失败 1-成功
     */
    private Integer loginStatus;

    /**
     * 登录方式：PASSWORD/WECHAT/QQ/EMAIL/PHONE
     */
    private String loginMethod;

    /**
     * 风险等级：0-正常 1-可疑 2-高危
     */
    private Integer riskLevel;

    /**
     * 风险原因
     */
    private String riskReason;

    /**
     * 是否为当前设备
     */
    private Boolean isCurrentDevice;

    private static final long serialVersionUID = 1L;
}
