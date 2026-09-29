package com.study.chat.module.realtime;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 当前后端节点标识：多实例部署时用于判断 WebSocket session 是否在本机。
 */
@Component
public class NodeContext {

    private final String nodeId = UUID.randomUUID().toString();

    public String getNodeId() {
        return nodeId;
    }
}
