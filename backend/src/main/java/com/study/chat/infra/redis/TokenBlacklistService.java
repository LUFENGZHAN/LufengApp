package com.study.chat.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * access token 黑名单：登出或 refresh 轮换时把旧 jti 拉黑，TTL 取该 token 的剩余有效期。
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redis;

    public void blacklist(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        long ttlSeconds = Math.max(1, expiresAt.getEpochSecond() - Instant.now().getEpochSecond());
        redis.opsForValue().set(RedisKeys.jwtBlacklist(jti), "1", Duration.ofSeconds(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(RedisKeys.jwtBlacklist(jti)));
    }
}
