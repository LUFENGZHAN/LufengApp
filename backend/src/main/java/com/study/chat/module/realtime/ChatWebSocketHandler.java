package com.study.chat.module.realtime;

import com.study.chat.infra.redis.OnlineNode;
import com.study.chat.infra.redis.PresenceService;
import com.study.chat.module.conversation.service.ConversationService;
import com.study.chat.module.message.dto.MessageVO;
import com.study.chat.module.message.dto.SendMessageReq;
import com.study.chat.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 长连接收发：心跳、发消息、已读上报、输入中。
 * <p>
 * 上行消息与 HTTP 接口走同一个 MessageService，保证幂等与持久化语义一致；
 * WS 只负责实时性，失败时客户端降级到 HTTP 重发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final WsSessionRegistry registry;
    private final PresenceService presenceService;
    private final NodeContext nodeContext;
    private final MessageDispatcher dispatcher;
    private final MessageService messageService;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        String deviceId = (String) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_DEVICE_ID);
        String platform = (String) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_PLATFORM);
        if (userId == null || deviceId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        registry.register(userId, deviceId, platform, session);
        presenceService.online(userId, new OnlineNode(nodeContext.getNodeId(), session.getId(),
                platform == null ? "web" : platform, deviceId, System.currentTimeMillis()));

        send(userId, deviceId, WsEnvelope.of(WsMessageType.CONNECTED,
                Map.of("userId", userId, "deviceId", deviceId, "serverTime", System.currentTimeMillis())));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        String deviceId = (String) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_DEVICE_ID);
        if (userId == null || deviceId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        registry.touch(userId, deviceId);
        presenceService.online(userId, new OnlineNode(nodeContext.getNodeId(), session.getId(),
                resolvePlatform(session), deviceId, System.currentTimeMillis()));

        WsEnvelope envelope;
        try {
            envelope = objectMapper.readValue(message.getPayload(), WsEnvelope.class);
        } catch (Exception e) {
            send(userId, deviceId, WsEnvelope.of(WsMessageType.ERROR,
                    Map.of("code", 10001, "message", "消息格式不正确")));
            return;
        }
        if (envelope == null || envelope.getType() == null) {
            return;
        }

        switch (envelope.getType().toLowerCase()) {
            case "ping" -> send(userId, deviceId, WsEnvelope.of(WsMessageType.PONG,
                    Map.of("ts", System.currentTimeMillis())));
            case "chat" -> handleChat(userId, deviceId, envelope);
            case "read" -> handleRead(userId, deviceId, envelope);
            case "typing" -> handleTyping(userId, deviceId, envelope);
            default -> log.debug("忽略未知 WS 消息类型：{}", envelope.getType());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WS 传输异常 sessionId={}：{}", session.getId(), exception.getMessage());
        cleanup(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session);
    }

    private void handleChat(long userId, String deviceId, WsEnvelope envelope) {
        try {
            SendMessageReq req = objectMapper.convertValue(envelope.getData(), SendMessageReq.class);
            MessageVO vo = messageService.send(userId, deviceId, req);
            // ACK 只回本端；其他端与接收方由 MessageSentEvent 推送
            // 带上 conversationId：首条消息可能是「按 peerId 懒创建会话」，
            // 客户端要靠它把临时会话切换成真实会话。
            send(userId, deviceId, WsEnvelope.of(WsMessageType.ACK, vo.getSeq(),
                    Map.of("clientMsgId", vo.getClientMsgId(), "msgNo", vo.getMsgNo(),
                            "seq", vo.getSeq(), "sendTime", vo.getSendTime(),
                            "conversationId", vo.getConversationId(),
                            "duplicated", vo.getDuplicated())));
        } catch (Exception e) {
            log.warn("WS 发送消息失败 userId={}：{}", userId, e.getMessage());
            send(userId, deviceId, WsEnvelope.of(WsMessageType.ERROR,
                    Map.of("message", e.getMessage() == null ? "发送失败" : e.getMessage())));
        }
    }

    private void handleRead(long userId, String deviceId, WsEnvelope envelope) {
        try {
            Map<?, ?> data = (Map<?, ?>) envelope.getData();
            Long conversationId = toLong(data.get("conversationId"));
            Long seq = toLong(data.get("lastReadSeq"));
            if (conversationId == null || seq == null) {
                return;
            }
            conversationService.markRead(userId, conversationId, seq, deviceId);
        } catch (Exception e) {
            log.warn("WS 已读上报失败 userId={}：{}", userId, e.getMessage());
        }
    }

    private void handleTyping(long userId, String deviceId, WsEnvelope envelope) {
        try {
            Map<?, ?> data = (Map<?, ?>) envelope.getData();
            Long conversationId = toLong(data.get("conversationId"));
            if (conversationId == null) {
                return;
            }
            WsEnvelope typing = WsEnvelope.of(WsMessageType.TYPING,
                    Map.of("conversationId", conversationId, "userId", userId));
            conversationService.memberIds(conversationId).stream()
                    .filter(id -> !id.equals(userId))
                    .forEach(id -> dispatcher.dispatchToUser(id, typing, null));
        } catch (Exception e) {
            log.debug("WS typing 处理失败：{}", e.getMessage());
        }
    }

    private void cleanup(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_USER_ID);
        String deviceId = (String) session.getAttributes().get(AuthHandshakeInterceptor.ATTR_DEVICE_ID);
        if (userId == null || deviceId == null) {
            return;
        }
        registry.unregister(userId, deviceId);
        presenceService.offline(userId, deviceId);
    }

    private void send(long userId, String deviceId, WsEnvelope envelope) {
        try {
            dispatcher.sendLocal(userId, deviceId, objectMapper.writeValueAsString(envelope));
        } catch (Exception e) {
            log.warn("WS 下发失败 userId={} type={}", userId, envelope.getType());
        }
    }

    private String resolvePlatform(WebSocketSession session) {
        Object platform = session.getAttributes().get(AuthHandshakeInterceptor.ATTR_PLATFORM);
        return platform == null ? "web" : String.valueOf(platform);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        return Long.parseLong(String.valueOf(value));
    }
}
