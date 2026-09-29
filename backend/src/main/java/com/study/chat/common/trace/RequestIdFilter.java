package com.study.chat.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 为每个请求生成 requestId，写入 MDC 与响应头。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestIdUtil.HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdUtil.generate();
        }
        RequestIdUtil.set(requestId);
        response.setHeader(RequestIdUtil.HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            RequestIdUtil.clear();
        }
    }
}
