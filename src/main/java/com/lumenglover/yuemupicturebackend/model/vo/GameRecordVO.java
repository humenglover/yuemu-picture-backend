package com.lumenglover.yuemupicturebackend.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 统一游戏记录视图对象类 (包含用户信息以支持排行榜展示)
 */
@Data
public class GameRecordVO implements Serializable {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 游戏类型 (英文标识)
     */
    private String gameType;

    /**
     * 游戏名称 (中文)
     */
    private String gameName;

    /**
     * 关卡/难度等级 (可选)
     */
    private String level;

    /**
     * 成绩/得分
     */
    private Integer score;

    /**
     * 记录创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
