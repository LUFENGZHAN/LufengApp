package com.study.chat.module.message.service;

import com.study.chat.common.model.PageVO;
import com.study.chat.module.message.dto.MessageVO;
import com.study.chat.module.message.dto.SendMessageReq;
import com.study.chat.module.message.dto.SyncReq;

import java.util.List;

public interface MessageService {

    /**
     * 发送消息：幂等（clientMsgId）、事务落库、事务提交后推送。
     */
    MessageVO send(long senderId, String excludeDeviceId, SendMessageReq req);

    /**
     * 历史消息游标分页。cursor 为空表示取最新一页。
     */
    PageVO<MessageVO> history(long userId, Long conversationId, Long cursor, int size);

    /**
     * 离线补拉：返回各会话中 seq 大于 afterSeq 的消息，并推进 ack 位点。
     */
    List<MessageVO> sync(long userId, SyncReq req);

    /**
     * 撤回：仅发送者本人、仅 2 分钟内。
     */
    void recall(long userId, String msgNo, String excludeDeviceId);
}
