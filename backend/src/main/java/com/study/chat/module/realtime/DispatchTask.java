package com.study.chat.module.realtime;

/**
 * 跨节点投递任务：本机没有目标 session 时，通过 Redis Pub/Sub 交给持有该连接的节点。
 */
public record DispatchTask(String targetNodeId,
                           long userId,
                           String deviceId,
                           String payload) {
}
