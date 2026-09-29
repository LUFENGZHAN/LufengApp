package com.study.chat.infra.redis;

import com.study.chat.infra.mapper.SeqAllocatorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 会话内消息序号分配：Redis INCR 为主，数据库号段表为降级方案。
 * <p>
 * 数据库侧有 uk(conversation_id, seq) 唯一索引做最终兜底，重复时由调用方重新取号重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeqService {

    private final StringRedisTemplate redis;
    private final SeqAllocatorMapper seqAllocatorMapper;

    public long nextConversationSeq(long conversationId) {
        String key = RedisKeys.seqConversation(conversationId);
        try {
            Long seq = redis.opsForValue().increment(key);
            if (seq != null) {
                return seq;
            }
        } catch (RuntimeException e) {
            log.warn("Redis 分配 seq 失败，降级到数据库 bizKey={}", key, e);
        }
        return allocateFromDb(key);
    }

    /**
     * 数据库降级：乐观锁重试，最多 5 次。
     */
    private long allocateFromDb(String bizKey) {
        for (int i = 0; i < 5; i++) {
            Long current = seqAllocatorMapper.selectCurrent(bizKey);
            Long version = seqAllocatorMapper.selectVersion(bizKey);
            if (current == null || version == null) {
                throw new DuplicateKeyException("号段未初始化：" + bizKey);
            }
            long next = current + 1;
            if (seqAllocatorMapper.increase(bizKey, next, version) == 1) {
                return next;
            }
        }
        throw new IllegalStateException("seq 分配失败：" + bizKey);
    }
}
