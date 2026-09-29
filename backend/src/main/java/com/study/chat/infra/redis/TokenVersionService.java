package com.study.chat.infra.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.IntSupplier;

/**
 * 用户令牌版本：JWT 无法主动失效，靠 token_version 实现「改密码 / 一键下线所有设备」。
 * <p>
 * 读取优先走 Redis，未命中则用数据库值回填；升级时先写库再刷新缓存。
 */
@Service
@RequiredArgsConstructor
public class TokenVersionService {

    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final StringRedisTemplate redis;

    public int getOrLoad(long userId, IntSupplier dbLoader) {
        String key = RedisKeys.tokenVersion(userId);
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return Integer.parseInt(cached);
        }
        int version = dbLoader.getAsInt();
        redis.opsForValue().set(key, String.valueOf(version), CACHE_TTL);
        return version;
    }

    /**
     * 递增令牌版本：先落库，再更新缓存，使该用户所有已签发 JWT 立即失效。
     */
    public void bump(long userId, int newVersion) {
        redis.opsForValue().set(RedisKeys.tokenVersion(userId), String.valueOf(newVersion), CACHE_TTL);
    }
}
