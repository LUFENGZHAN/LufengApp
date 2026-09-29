package com.study.chat.module.realtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 订阅跨节点投递主题，只处理目标节点为本机的任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDispatchListener implements MessageListener {

    private final MessageDispatcher dispatcher;
    private final NodeContext nodeContext;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            DispatchTask task = objectMapper.readValue(message.getBody(), DispatchTask.class);
            if (!nodeContext.getNodeId().equals(task.targetNodeId())) {
                return;
            }
            dispatcher.sendLocal(task.userId(), task.deviceId(), task.payload());
        } catch (Exception e) {
            log.error("处理跨节点投递任务失败", e);
        }
    }
}
