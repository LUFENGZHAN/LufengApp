package com.study.chat.common.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Data
@ConfigurationProperties(prefix = "lufeng.jwt")
public class JwtProperties {

    /**
     * HMAC 密钥，HS256 要求至少 32 字节
     */
    private String secret;

    private Duration accessTtl = Duration.ofHours(2);

    private Duration refreshTtl = Duration.ofDays(30);
}
