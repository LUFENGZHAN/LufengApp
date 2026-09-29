package com.study.chat.module.message.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息行：消息 + 发送者资料，避免列表接口二次查询。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageRow extends Message {

    private String senderNo;

    private String senderNickname;

    private String senderAvatar;
}
