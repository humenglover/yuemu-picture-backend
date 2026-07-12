package com.lumenglover.yuemupicturebackend.model.dto.gamerecord;

import com.lumenglover.yuemupicturebackend.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.io.Serializable;

/**
 * 游戏记录及排行榜通用查询请求类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class GameRecordQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户ID (可指定查询某特定用户历史)
     */
    private Long userId;

    /**
     * 游戏类型 (英文标识，例如 "aa", "cat_trap", "tetris")
     */
    private String gameType;

    /**
     * 关卡/难度等级 (可选)
     */
    private String level;

    private static final long serialVersionUID = 1L;
}
