package com.lumenglover.yuemupicturebackend.model.dto.gamerecord;

import lombok.Data;
import java.io.Serializable;

/**
 * 保存游戏记录请求类
 */
@Data
public class GameRecordAddRequest implements Serializable {

    /**
     * 游戏类型 (英文标识，例如 "aa", "cat_trap", "tetris")
     */
    private String gameType;

    /**
     * 关卡/难度等级 (对于若干关卡的游戏可选填，若无则传 null 或 default)
     */
    private String level;

    /**
     * 得分成绩
     */
    private Integer score;

    private static final long serialVersionUID = 1L;
}
