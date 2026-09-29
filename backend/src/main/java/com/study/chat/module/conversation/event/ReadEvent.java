package com.study.chat.module.conversation.event;

import java.util.List;

/**
 * 已读事件：事务提交后推送给对端，用于展示「已读」。
 */
public record ReadEvent(Long conversationId,
                        Long userId,
                        Long lastReadSeq,
                        List<Long> recipientIds,
                        String excludeDeviceId) {
}
