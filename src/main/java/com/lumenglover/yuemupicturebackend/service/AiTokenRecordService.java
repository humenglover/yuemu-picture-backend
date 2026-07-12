package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.entity.AiTokenRecord;

/**
 * AI token 使用流水记录及限额服务
 */
public interface AiTokenRecordService extends IService<AiTokenRecord> {

    /**
     * 校验用户 AI Token 额度水位
     * 若超过限额则抛出业务异常
     * @param userId 用户ID
     */
    void checkTokenQuota(Long userId);

    /**
     * 记录并更新用户的 AI Token 使用量
     * @param userId 用户ID
     * @param consumeToken 本次调用的 token 数量
     */
    void recordTokenUsage(Long userId, int consumeToken);

    /**
     * 获取用户 AI Token 使用情况
     * @param userId 用户ID
     * @return 包含当前配额和使用情况的视图对象
     */
    com.lumenglover.yuemupicturebackend.model.vo.AiTokenUsageVO getTokenUsage(Long userId);

    /**
     * 校验并扣减每周图片生成额度
     * @param userId 用户 ID
     * @return 是否成功扣减（如果额度不足返回 false）
     */
    boolean checkAndDeductImageGenQuota(Long userId);

    /**
     * 校验并扣减每周以图搜图额度
     * @param userId 用户 ID
     * @return 是否成功扣减（如果额度不足返回 false）
     */
    boolean checkAndDeductImageSearchQuota(Long userId);
}
