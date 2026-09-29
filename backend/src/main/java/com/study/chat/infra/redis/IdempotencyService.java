package com.study.chat.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 通用幂等：Redis SET NX，用于注册、加好友、创建会话等「重试不能产生副作用」的写操作。
 * 消息发送的幂等由数据库唯一索引兜底（uk_conv_client_msg），不走这里。
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final StringRedisTemplate redis;

    public boolean tryAcquire(String bizType, String bizKey, Duration ttl) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent(RedisKeys.idempotent(bizType, bizKey), "1", ttl);
        return Boolean.TRUE.equals(ok);
    }

    public void release(String bizType, String bizKey) {
        redis.delete(RedisKeys.idempotent(bizType, bizKey));
    }
}
