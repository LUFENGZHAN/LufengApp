package com.study.chat.module.realtime;

import com.study.chat.module.conversation.event.ReadEvent;
import com.study.chat.module.friend.event.FriendNotifyEvent;
import com.study.chat.module.message.event.MessageRecalledEvent;
import com.study.chat.module.message.event.MessageSentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 事务提交后的实时推送：先落库再推送，保证「推送到的消息一定能被 sync 查到」。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeEventListener {

    private final MessageDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        WsEnvelope envelope = WsEnvelope.of(WsMessageType.CHAT, event.message().getSeq(), event.message());
        // 发送者的其他端：排除发起端自身，避免本端重复渲染
        dispatcher.dispatchToUser(event.message().getSenderId(), envelope, event.excludeDeviceId());
        for (Long recipientId : event.recipientIds()) {
            dispatcher.dispatchToUser(recipientId, envelope, null);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRead(ReadEvent event) {
        WsEnvelope envelope = WsEnvelope.of(WsMessageType.READ, event.lastReadSeq(),
                Map.of("conversationId", event.conversationId(),
                        "userId", event.userId(),
                        "lastReadSeq", event.lastReadSeq()));
        for (Long recipientId : event.recipientIds()) {
            dispatcher.dispatchToUser(recipientId, envelope, event.excludeDeviceId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRecalled(MessageRecalledEvent event) {
        WsEnvelope envelope = WsEnvelope.of(WsMessageType.NOTIFY, event.seq(),
                Map.of("kind", "recall",
                        "conversationId", event.conversationId(),
                        "msgNo", event.msgNo(),
                        "seq", event.seq()));
        for (Long recipientId : event.recipientIds()) {
            dispatcher.dispatchToUser(recipientId, envelope, event.excludeDeviceId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFriendNotify(FriendNotifyEvent event) {
        WsEnvelope envelope = WsEnvelope.of(WsMessageType.NOTIFY,
                Map.of("kind", event.kind(), "payload", event.payload()));
        dispatcher.dispatchToUser(event.targetUserId(), envelope, null);
    }
}
