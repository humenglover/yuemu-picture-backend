package com.lumenglover.yuemupicturebackend.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * AI调用流水表
 * @TableName ai_token_record
 */
@TableName(value ="ai_token_record")
@Data
public class AiTokenRecord implements Serializable {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 操作用户ID
     */
    private Long userId;

    /**
     * 本次调用消耗Token数量
     */
    private Integer consumeToken;

    /**
     * 调用时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 软删除标识，0=正常，1=已删除
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
