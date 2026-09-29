package com.study.chat.module.conversation.dto;

import lombok.Data;

/**
 * 会话视图：会话列表与会话详情共用。
 */
@Data
public class ConversationVO {

    private Long conversationId;

    private String conversationNo;

    /**
     * 1单聊 2群聊 3系统通知
     */
    private Integer type;

    private String title;

    private String avatar;

    private PeerVO peer;

    private LastMessageVO lastMessage;

    private Long unreadCount;

    private Long lastReadSeq;

    private Long lastAckSeq;

    private Long lastMessageAt;

    private Integer muted;

    @Data
    public static class PeerVO {

        private Long userId;

        private String userNo;

        private String nickname;

        private String avatar;

        private Boolean online;
    }

    @Data
    public static class LastMessageVO {

        private Long msgId;

        private String msgNo;

        private Long seq;

        private Long senderId;

        private Integer msgType;

        private String preview;

        private Long sendTime;
    }
}
