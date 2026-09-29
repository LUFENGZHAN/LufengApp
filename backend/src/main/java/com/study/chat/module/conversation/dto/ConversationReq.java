package com.study.chat.module.conversation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConversationReq {

    /**
     * 创建单聊会话时的对端用户 ID
     */
    @NotNull(message = "targetUserId 不能为空")
    private Long targetUserId;
}
