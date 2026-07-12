package com.lumenglover.yuemupicturebackend.service;

import com.lumenglover.yuemupicturebackend.model.entity.AiResource;
import com.baomidou.mybatisplus.extension.service.IService;
import java.util.List;

/**
* @author lumenglover
* @description 针对表【ai_resource(AI资源库表)】的数据库操作Service
* @createDate 2024-05-23
*/
public interface AiResourceService extends IService<AiResource> {

    /**
     * 批量提取并保存AI返回内容中的资源链接
     * @param content AI回复内容
     * @param messageId 关联的消息ID
     * @param userId 所属用户ID
     */
    void extractAndSaveResources(String content, Long messageId, Long userId);

    /**
     * 获取指定消息的资源列表
     * @param messageId
     * @return
     */
    List<AiResource> getResourcesByMessageId(Long messageId);
}
