package com.study.chat.module.realtime;

import lombok.Data;

/**
 * WebSocket 统一信封。Web 与 App 解析同一份结构。
 *
 * @param type 见 {@link WsMessageType}
 * @param seq  关联序号（消息场景为会话 seq，其他场景可为空）
 * @param ts   服务端毫秒时间戳
 * @param data 业务载荷
 */
@Data
public class WsEnvelope {

    private String type;

    private Long seq;

    private Long ts;

    private Object data;

    public WsEnvelope() {
    }

    public WsEnvelope(String type, Long seq, Object data) {
        this.type = type;
        this.seq = seq;
        this.data = data;
        this.ts = System.currentTimeMillis();
    }

    public static WsEnvelope of(WsMessageType type, Object data) {
        return new WsEnvelope(type.name().toLowerCase(), null, data);
    }

    public static WsEnvelope of(WsMessageType type, Long seq, Object data) {
        return new WsEnvelope(type.name().toLowerCase(), seq, data);
    }
}
