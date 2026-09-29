package com.study.chat.module.realtime;

import com.study.chat.common.auth.AuthenticationInterceptor;
import com.study.chat.common.auth.UserContext;
import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

/**
 * WebSocket 握手鉴权：与业务接口复用同一套 JWT 校验逻辑，失败拒绝升级连接。
 * <p>
 * 浏览器原生 WebSocket 不能自定义请求头，因此同时支持：
 * <ol>
 *   <li>Authorization 头（App / 非浏览器客户端）</li>
 *   <li>URL 参数 token（浏览器）</li>
 *   <li>子协议 access_token.&lt;token&gt;（浏览器备选）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthHandshakeInterceptor implements HandshakeInterceptor {

    static final String ATTR_USER_ID = "uid";
    static final String ATTR_DEVICE_ID = "deviceId";
    static final String ATTR_PLATFORM = "platform";

    private static final String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    private final AuthenticationInterceptor authenticationInterceptor;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String token = resolveToken(request);
        if (token == null) {
            reject(response);
            return false;
        }
        try {
            UserContext context = authenticationInterceptor.authenticate(token, resolvePlatform(request));
            attributes.put(ATTR_USER_ID, context.userId());
            attributes.put(ATTR_DEVICE_ID, context.deviceId());
            attributes.put(ATTR_PLATFORM, context.clientType());
            return true;
        } catch (BizException e) {
            log.debug("WS 握手鉴权失败：code={} message={}", e.getCode(), e.getMessage());
            reject(response);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // no-op
    }

    private void reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().remove(SEC_WEBSOCKET_PROTOCOL);
    }

    private String resolveToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String query = servletRequest.getServletRequest().getParameter("token");
            if (query != null && !query.isBlank()) {
                return query.trim();
            }
        }
        List<String> protocols = request.getHeaders().get(SEC_WEBSOCKET_PROTOCOL);
        if (protocols != null) {
            for (String protocol : protocols) {
                for (String part : protocol.split(",")) {
                    String value = part.trim();
                    if (value.startsWith("access_token.")) {
                        return value.substring("access_token.".length());
                    }
                }
            }
        }
        return null;
    }

    private String resolvePlatform(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String platform = servletRequest.getServletRequest().getHeader("X-Client-Type");
            if (platform != null && !platform.isBlank()) {
                return platform.trim();
            }
        }
        return "web";
    }
}
