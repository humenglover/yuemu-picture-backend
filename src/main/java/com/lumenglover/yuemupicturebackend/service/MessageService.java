package com.lumenglover.yuemupicturebackend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lumenglover.yuemupicturebackend.model.dto.message.AddMessage;
import com.lumenglover.yuemupicturebackend.model.entity.Message;
import com.lumenglover.yuemupicturebackend.model.vo.MessageVO;

import java.util.List;


/**
* @author 鹿梦
* @description 针对表【message(留言板表)】的数据库操作Service
* @createDate 2025-01-03 16:28:14
*/
public interface MessageService extends IService<Message> {


    Boolean addMessage(AddMessage addMessage);

    List<MessageVO> getTop500();
}
