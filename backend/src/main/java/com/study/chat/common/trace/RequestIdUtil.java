package com.study.chat.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * 链路 ID：写入 MDC，并随响应头与响应体返回，客户端报错时可直接回传定位。
 */
public final class RequestIdUtil {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private RequestIdUtil() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static void set(String requestId) {
        MDC.put(MDC_KEY, requestId);
    }

    public static String get() {
        String id = MDC.get(MDC_KEY);
        return id == null ? "" : id;
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
