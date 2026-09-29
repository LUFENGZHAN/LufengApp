package com.study.chat.module.message.event;

import com.study.chat.module.message.dto.MessageVO;

import java.util.List;

/**
 * 消息已落库事件：事务提交后才推送，保证「推送到的消息一定能被 sync 查到」。
 */
public record MessageSentEvent(MessageVO message,
                               List<Long> recipientIds,
                               String excludeDeviceId) {
}
