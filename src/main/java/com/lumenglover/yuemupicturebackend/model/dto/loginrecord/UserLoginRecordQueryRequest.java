package com.lumenglover.yuemupicturebackend.model.dto.loginrecord;

import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户登录记录查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserLoginRecordQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 登录IP地址
     */
    private String loginIp;

    /**
     * 设备类型
     */
    private String deviceType;

    /**
     * 登录状态
     */
    private Integer loginStatus;

    /**
     * 登录方式
     */
    private String loginMethod;

    /**
     * 风险等级
     */
    private Integer riskLevel;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    private static final long serialVersionUID = 1L;
}
