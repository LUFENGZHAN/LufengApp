package com.study.chat.module.realtime;

import com.study.chat.infra.redis.RedisKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 跨节点 WS 投递：只有多实例部署时才需要，单实例下不开（避免 Redis 未就绪影响启动）。
 * <p>
 * 开启方式：lufeng.ws.cluster-enabled=true
 */
@Configuration
@ConditionalOnProperty(prefix = "lufeng.ws", name = "cluster-enabled", havingValue = "true")
public class RedisDispatchConfig {

    @Bean
    public RedisMessageListenerContainer dispatchContainer(RedisConnectionFactory factory,
                                                           RedisDispatchListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(listener, new ChannelTopic(RedisKeys.WS_DISPATCH_TOPIC));
        return container;
    }
}
