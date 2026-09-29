package com.study.chat.module.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 服务端心跳巡检：空闲连接主动关闭（触发客户端重连），避免半开连接静默丢消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WsHeartbeatScheduler {

    private final WsSessionRegistry registry;

    @Value("${lufeng.ws.heartbeat-interval-ms:25000}")
    private long heartbeatIntervalMs;

    @Value("${lufeng.ws.idle-timeout-ms:90000}")
    private long idleTimeoutMs;

    @Scheduled(fixedDelayString = "${lufeng.ws.heartbeat-interval-ms:25000}")
    public void scan() {
        registry.scan(heartbeatIntervalMs, idleTimeoutMs);
    }
}
