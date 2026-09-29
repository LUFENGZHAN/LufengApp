package com.study.chat.module.realtime;

import com.study.chat.infra.redis.OnlineNode;
import com.study.chat.infra.redis.PresenceService;
import com.study.chat.infra.redis.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * 消息分发：按「用户 + 设备」维度投递，支持多端在线与跨节点转发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageDispatcher {

    private final WsSessionRegistry registry;
    private final PresenceService presenceService;
    private final NodeContext nodeContext;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /**
     * @param excludeDeviceId 需要排除的设备（通常是发起端自身，避免本端重复渲染）
     */
    public void dispatchToUser(long userId, WsEnvelope envelope, String excludeDeviceId) {
        Map<String, OnlineNode> onlineNodes = presenceService.listOnline(userId);
        if (onlineNodes.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("WS 消息序列化失败 userId={} type={}", userId, envelope.getType(), e);
            return;
        }
        for (OnlineNode node : onlineNodes.values()) {
            if (excludeDeviceId != null && excludeDeviceId.equals(node.deviceId())) {
                continue;
            }
            if (nodeContext.getNodeId().equals(node.nodeId())) {
                sendLocal(userId, node.deviceId(), payload);
            } else {
                publish(node.nodeId(), userId, node.deviceId(), payload);
            }
        }
    }

    /**
     * 本机投递：失败即关闭连接，让客户端重连后走离线补拉，避免静默丢消息。
     */
    public void sendLocal(long userId, String deviceId, String payload) {
        WsSessionRegistry.SessionHolder holder = registry.find(userId, deviceId);
        if (holder == null || !holder.getSession().isOpen()) {
            return;
        }
        try {
            holder.getSession().sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.warn("WS 投递失败 userId={} deviceId={}，关闭连接触发重连", userId, deviceId);
            try {
                holder.getSession().close();
            } catch (Exception ignored) {
                // ignore
            }
            registry.unregister(userId, deviceId);
        }
    }

    private void publish(String targetNodeId, long userId, String deviceId, String payload) {
        try {
            redis.convertAndSend(RedisKeys.WS_DISPATCH_TOPIC,
                    objectMapper.writeValueAsString(new DispatchTask(targetNodeId, userId, deviceId, payload)));
        } catch (Exception e) {
            log.error("跨节点投递序列化失败 userId={}", userId, e);
        }
    }
}
