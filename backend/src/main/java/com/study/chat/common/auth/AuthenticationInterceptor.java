package com.study.chat.common.auth;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.infra.redis.TokenBlacklistService;
import com.study.chat.infra.redis.TokenVersionService;
import com.study.chat.module.auth.mapper.UserAccountMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Set;

/**
 * 业务接口身份校验：验签 JWT → 检查黑名单 → 比对 token_version → 注入用户上下文。
 * <p>
 * 校验失败统一抛 {@link BizException}，由 {@code GlobalExceptionHandler} 输出统一响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationInterceptor implements HandlerInterceptor {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/health"
    );

    static final String HEADER_DEVICE_ID = "X-Device-Id";
    static final String HEADER_CLIENT_TYPE = "X-Client-Type";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenVersionService tokenVersionService;
    private final UserAccountMapper userAccountMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // 静态资源、跨域预检、错误处理等非控制器请求直接放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        String uri = request.getRequestURI();
        if (PUBLIC_PATHS.contains(uri)) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }

        JwtClaims claims = jwtTokenProvider.parse(token);
        if (tokenBlacklistService.isBlacklisted(claims.jti())) {
            throw new BizException(ResultCode.REFRESH_INVALID);
        }
        int expectedVersion = tokenVersionService.getOrLoad(
                claims.uid(), () -> userAccountMapper.selectTokenVersion(claims.uid()));
        if (claims.tokenVersion() != expectedVersion) {
            throw new BizException(ResultCode.KICKED);
        }

        AuthContext.set(UserContext.of(claims, request.getHeader(HEADER_CLIENT_TYPE)));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        AuthContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    /**
     * 供 WebSocket 握手复用同一套校验逻辑。
     */
    public UserContext authenticate(String token, String clientType) {
        JwtClaims claims = jwtTokenProvider.parse(token);
        if (tokenBlacklistService.isBlacklisted(claims.jti())) {
            throw new BizException(ResultCode.REFRESH_INVALID);
        }
        int expectedVersion = tokenVersionService.getOrLoad(
                claims.uid(), () -> userAccountMapper.selectTokenVersion(claims.uid()));
        if (claims.tokenVersion() != expectedVersion) {
            throw new BizException(ResultCode.KICKED);
        }
        return UserContext.of(claims, clientType);
    }

    public static List<String> publicPaths() {
        return List.copyOf(PUBLIC_PATHS);
    }
}
