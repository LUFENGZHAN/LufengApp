package com.study.chat.module.message.event;

import java.util.List;

/**
 * 消息撤回事件：通知会话内其他人把本地消息替换为撤回提示。
 */
public record MessageRecalledEvent(Long conversationId,
                                   String msgNo,
                                   Long seq,
                                   Long senderId,
                                   List<Long> recipientIds,
                                   String excludeDeviceId) {
}
