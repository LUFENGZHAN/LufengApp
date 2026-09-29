package com.study.chat.module.conversation.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话列表行：会话 + 本人在该会话的位点 + 对端资料。
 */
@Data
public class ConversationRow {

    private Long conversationId;

    private String conversationNo;

    private Integer type;

    private String title;

    private String avatar;

    private Long lastMessageId;

    private Long lastMessageSeq;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private Long unreadCount;

    private Long lastReadSeq;

    private Long lastAckSeq;

    private Long joinSeq;

    private Integer muted;

    private Long peerId;

    private String peerNo;

    private String peerNickname;

    private String peerAvatar;
}
