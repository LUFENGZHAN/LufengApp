package com.study.chat.common.interceptor;


import com.study.chat.common.annotation.LoginRequired;
import com.study.chat.common.result.Result;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.method.HandlerMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;


@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {


    private final ObjectMapper objectMapper;


    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {


        if (!(handler instanceof HandlerMethod)) {
            return true;
        }


        HandlerMethod method = (HandlerMethod) handler;


        LoginRequired annotation =
                method.getMethodAnnotation(LoginRequired.class);


        if(annotation == null){
            return true;
        }


        String token = request.getHeader("Authorization");


        if(token == null || token.isEmpty()){

            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");

            response.getWriter().write(
                    objectMapper.writeValueAsString(
                            Result.error(401,"未登录")
                    )
            );
            return false;
        }


        return true;
    }
}