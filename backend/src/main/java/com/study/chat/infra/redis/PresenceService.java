package com.study.chat.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 多端在线状态：Redis Hash（field = deviceId）+ 心跳续期 TTL。
 * <p>
 * 进程异常退出时不会留下僵尸在线记录——TTL 到期即自动剔除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService {

    /** 心跳周期的 1.5 倍，Redis 侧兜底剔除僵尸连接 */
    private static final Duration TTL = Duration.ofSeconds(45);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void online(long userId, OnlineNode node) {
        try {
            redis.opsForHash().put(RedisKeys.online(userId), node.deviceId(), objectMapper.writeValueAsString(node));
            redis.expire(RedisKeys.online(userId), TTL);
        } catch (Exception e) {
            log.error("写入在线状态失败 userId={}", userId, e);
        }
    }

    public void offline(long userId, String deviceId) {
        try {
            redis.opsForHash().delete(RedisKeys.online(userId), deviceId);
        } catch (RuntimeException e) {
            log.warn("清理在线状态失败 userId={}", userId, e);
        }
    }

    public boolean isOnline(long userId) {
        try {
            Long size = redis.opsForHash().size(RedisKeys.online(userId));
            return size != null && size > 0;
        } catch (RuntimeException e) {
            log.warn("查询在线状态失败 userId={}", userId);
            return false;
        }
    }

    /**
     * 返回该用户全部在线端点。
     */
    public Map<String, OnlineNode> listOnline(long userId) {
        Map<Object, Object> entries;
        try {
            entries = redis.opsForHash().entries(RedisKeys.online(userId));
        } catch (RuntimeException e) {
            log.warn("读取在线状态失败 userId={}", userId);
            return Map.of();
        }
        Map<String, OnlineNode> result = new HashMap<>();
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            try {
                result.put(String.valueOf(entry.getKey()),
                        objectMapper.readValue(String.valueOf(entry.getValue()), OnlineNode.class));
            } catch (Exception e) {
                log.warn("在线状态反序列化失败 userId={} deviceId={}", userId, entry.getKey());
            }
        }
        return result;
    }
}
