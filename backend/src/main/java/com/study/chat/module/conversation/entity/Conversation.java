package com.study.chat.module.conversation.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话：1单聊 2群聊 3系统通知
 */
@Data
public class Conversation {

    private Long id;

    private String conversationNo;

    private Integer type;

    private Long ownerId;

    private String title;

    private String avatar;

    private Integer memberCount;

    private Long lastMessageId;

    private Long lastMessageSeq;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
