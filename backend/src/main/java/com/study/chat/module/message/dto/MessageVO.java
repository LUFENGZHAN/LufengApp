package com.study.chat.module.message.dto;

import lombok.Data;

/**
 * 消息视图：Web 与 App 共用。
 */
@Data
public class MessageVO {

    private Long msgId;

    private String msgNo;

    /**
     * 回传客户端幂等 ID，用于本地「发送中 → 已发送」状态收敛
     */
    private String clientMsgId;

    private Long conversationId;

    /**
     * 会话内递增序号：排序、分页、离线补拉的唯一基准
     */
    private Long seq;

    private Long senderId;

    private String senderNo;

    private String senderNickname;

    private String senderAvatar;

    private Integer msgType;

    private String content;

    private String extra;

    /**
     * 1正常 2已撤回 3已删除
     */
    private Integer status;

    /**
     * 服务端时间（毫秒），客户端不得用本地时钟排序
     */
    private Long sendTime;

    /**
     * true 表示命中幂等（重复提交），客户端按成功处理
     */
    private Boolean duplicated;
}
