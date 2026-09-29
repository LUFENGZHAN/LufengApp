package com.study.chat.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 固定窗口限流（Lua 保证 INCR 与 EXPIRE 原子执行）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiter {

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('incr', KEYS[1]) "
                    + "if c == 1 then redis.call('expire', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private final StringRedisTemplate redis;

    /**
     * @param window 统计窗口
     * @param limit  窗口内允许的次数
     * @return true 表示放行
     */
    /**
     * Redis 不可用时 fail-open：限流是防护手段，不应让主流程不可用。
     */
    public boolean allow(String key, Duration window, long limit) {
        try {
            Long count = redis.execute(SCRIPT, List.of(RedisKeys.rate(key)), String.valueOf(window.getSeconds()));
            return count != null && count <= limit;
        } catch (RuntimeException e) {
            log.warn("限流不可用，放行请求 key={}", key, e);
            return true;
        }
    }

    public void reset(String key) {
        redis.delete(RedisKeys.rate(key));
    }
}
