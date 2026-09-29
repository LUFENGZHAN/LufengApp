package com.study.chat.infra.redis;

/**
 * 一个在线端点：同一用户的多端（web / android）各一条记录。
 *
 * @param nodeId    后端节点 ID，用于判断 session 是否在本机
 * @param sessionId WebSocket session ID
 * @param platform  web / android / ios / desktop
 * @param deviceId  设备唯一 ID
 * @param loginAt   上线时间戳（毫秒）
 */
public record OnlineNode(String nodeId,
                         String sessionId,
                         String platform,
                         String deviceId,
                         long loginAt) {
}
