package com.lumenglover.yuemupicturebackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 游戏类型枚举
 */
@Getter
public enum GameTypeEnum {

    AA(1, "AA游戏", "aa"),
    BOX_JUMP(2, "盒子跳跃", "box_jump"),
    BRICK(3, "弹球打砖块", "brick"),
    CAT_TRAP(4, "圈小猫", "cat_trap"),
    COLOR_CHALLENGE(5, "色彩挑战", "color_challenge"),
    DINO(6, "谷歌小恐龙", "dino"),
    DRAW_LINE(7, "画线", "draw_line"),
    FRUIT_SLICE(8, "切水果", "fruit_slice"),
    GAME_2048(9, "2048", "2048"),
    LINK_LINK(10, "连连看", "link_link"),
    MAZE_RUNNER(11, "迷宫寻路", "maze_runner"),
    MEMORY_CARD(12, "记忆翻牌", "memory_card"),
    MINESWEEPER(13, "扫雷", "minesweeper"),
    PLANE_WAR(14, "飞机大战", "plane_war"),
    QUEENS(15, "八皇后", "queens"),
    SBTI(16, "别踩白块", "sbti"),
    SLIDING_PUZZLE(17, "数字拼图", "sliding_puzzle"),
    SNAKE(18, "贪吃蛇", "snake"),
    SUDOKU(19, "数独", "sudoku"),
    TANK_BATTLE(20, "坦克大战", "tank_battle"),
    TETRIS(21, "俄罗斯方块", "tetris"),
    WHACK_MOLE(22, "打地鼠", "whack_mole");

    private final int code;
    private final String text;
    private final String value;

    GameTypeEnum(int code, String text, String value) {
        this.code = code;
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举的 value
     * @return 枚举
     */
    public static GameTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
            if (gameTypeEnum.value.equalsIgnoreCase(value)) {
                return gameTypeEnum;
            }
        }
        return null;
    }

    /**
     * 根据 code 获取枚举
     *
     * @param code 数字编码
     * @return 枚举
     */
    public static GameTypeEnum getEnumByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (GameTypeEnum gameTypeEnum : GameTypeEnum.values()) {
            if (gameTypeEnum.code == code) {
                return gameTypeEnum;
            }
        }
        return null;
    }
}
