package com.study.chat.module.conversation.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话成员：承载未读数、已读位点与离线补拉位点。
 */
@Data
public class ConversationMember {

    private Long id;

    private Long conversationId;

    private Long userId;

    private Integer role;

    /** 加入时位点，早于该 seq 的消息不可见 */
    private Long joinSeq;

    /** 已确认接收的最大 seq（离线补拉起点） */
    private Long lastAckSeq;

    /** 已读位点 */
    private Long lastReadSeq;

    private Integer unreadCount;

    private Integer muted;

    private Integer isDeleted;

    private LocalDateTime joinedAt;

    private LocalDateTime updatedAt;
}
