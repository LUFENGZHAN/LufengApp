package com.study.chat.module.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本机 WebSocket 会话注册表：userId → deviceId → session。
 * <p>
 * session 用 {@link ConcurrentWebSocketSessionDecorator} 包装，允许多线程并发发送。
 */
@Slf4j
@Component
public class WsSessionRegistry {

    private static final int SEND_BUFFER_LIMIT = 8 * 1024;
    private static final int SEND_TIME_LIMIT = 5000;

    private final ConcurrentHashMap<Long, Map<String, SessionHolder>> sessions = new ConcurrentHashMap<>();

    public void register(long userId, String deviceId, String platform, WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session, SEND_TIME_LIMIT, SEND_BUFFER_LIMIT);
        SessionHolder holder = new SessionHolder(decorated, deviceId, platform);
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(deviceId, holder);
        log.info("WS 注册 userId={} deviceId={} sessionId={}", userId, deviceId, session.getId());
    }

    public void unregister(long userId, String deviceId) {
        Map<String, SessionHolder> map = sessions.get(userId);
        if (map == null) {
            return;
        }
        map.remove(deviceId);
        if (map.isEmpty()) {
            sessions.remove(userId);
        }
    }

    public SessionHolder find(long userId, String deviceId) {
        Map<String, SessionHolder> map = sessions.get(userId);
        return map == null ? null : map.get(deviceId);
    }

    public List<SessionHolder> list(long userId) {
        Map<String, SessionHolder> map = sessions.get(userId);
        return map == null ? List.of() : new ArrayList<>(map.values());
    }

    public void touch(long userId, String deviceId) {
        SessionHolder holder = find(userId, deviceId);
        if (holder != null) {
            holder.setLastHeartbeatAt(System.currentTimeMillis());
        }
    }

    /**
     * 心跳巡检：超时连接直接关闭，触发客户端重连，避免静默丢消息。
     */
    public void scan(long heartbeatIntervalMs, long idleTimeoutMs) {
        long now = System.currentTimeMillis();
        for (Map.Entry<Long, Map<String, SessionHolder>> userEntry : sessions.entrySet()) {
            for (Map.Entry<String, SessionHolder> deviceEntry : userEntry.getValue().entrySet()) {
                SessionHolder holder = deviceEntry.getValue();
                long idle = now - holder.getLastHeartbeatAt();
                if (idle > idleTimeoutMs) {
                    log.info("WS 心跳超时，关闭连接 userId={} deviceId={}", userEntry.getKey(), deviceEntry.getKey());
                    close(holder);
                    unregister(userEntry.getKey(), deviceEntry.getKey());
                } else if (idle > heartbeatIntervalMs && holder.getSession().isOpen()) {
                    try {
                        holder.getSession().sendMessage(holder.pingMessage());
                    } catch (IOException e) {
                        log.warn("WS 下发心跳失败 userId={}", userEntry.getKey());
                        close(holder);
                        unregister(userEntry.getKey(), deviceEntry.getKey());
                    }
                }
            }
        }
    }

    private void close(SessionHolder holder) {
        try {
            holder.getSession().close(CloseStatus.SESSION_NOT_RELIABLE);
        } catch (IOException e) {
            log.debug("关闭 WS 连接异常：{}", e.getMessage());
        }
    }

    public static class SessionHolder {

        private final WebSocketSession session;
        private final String deviceId;
        private final String platform;
        private volatile long lastHeartbeatAt = System.currentTimeMillis();

        public SessionHolder(WebSocketSession session, String deviceId, String platform) {
            this.session = session;
            this.deviceId = deviceId;
            this.platform = platform;
        }

        public WebSocketSession getSession() {
            return session;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getPlatform() {
            return platform;
        }

        public long getLastHeartbeatAt() {
            return lastHeartbeatAt;
        }

        public void setLastHeartbeatAt(long lastHeartbeatAt) {
            this.lastHeartbeatAt = lastHeartbeatAt;
        }

        public org.springframework.web.socket.TextMessage pingMessage() {
            return new org.springframework.web.socket.TextMessage(
                    "{\"type\":\"ping\",\"ts\":" + System.currentTimeMillis() + "}");
        }
    }
}
