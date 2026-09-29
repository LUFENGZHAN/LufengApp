package com.study.chat.module.realtime;

/**
 * WebSocket 协议消息类型。
 */
public enum WsMessageType {

    /** 握手成功 */
    CONNECTED,

    /** 新消息（双向） */
    CHAT,

    /** 上行消息落库确认 */
    ACK,

    /** 已读回执 */
    READ,

    /** 正在输入 */
    TYPING,

    /** 系统通知：好友申请、撤回、上下线 */
    NOTIFY,

    /** 心跳 */
    PING,
    PONG,

    /** 强制下线 */
    KICK,

    /** 协议级错误 */
    ERROR
}
