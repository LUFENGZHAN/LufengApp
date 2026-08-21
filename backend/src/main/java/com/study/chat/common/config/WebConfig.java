package com.study.chat.common.config;

import com.study.chat.common.interceptor.LoginInterceptor;
import com.study.chat.common.resolver.RequestDTOResolver;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;


@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {


    private final LoginInterceptor loginInterceptor;

    private final RequestDTOResolver requestDTOResolver;


    /**
     * 登录拦截
     */
    @Override
    public void addInterceptors(
            InterceptorRegistry registry
    ) {

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**");

    }


    /**
     * 自定义参数解析
     */
    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers
    ) {

        resolvers.add(requestDTOResolver);

    }

}