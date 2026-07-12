package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 统一游戏记录实体类
 */
@TableName(value = "game_record")
@Data
public class GameRecord implements Serializable {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 游戏类型 (英文标识)
     */
    private String gameType;

    /**
     * 游戏名称 (中文)
     */
    private String gameName;

    /**
     * 关卡/难度等级 (可选，可为空)
     */
    private String level;

    /**
     * 当前关卡分数/用时成绩
     */
    private Integer score;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    /**
     * 是否删除 (0-未删除，1-已删除)
     */
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}
