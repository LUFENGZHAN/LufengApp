package com.study.chat.common.config;

import com.study.chat.common.auth.AuthenticationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：静态资源映射 + 登录拦截 + 跨域。
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;

    /**
     * 上传文件落地目录（绝对路径）。默认落在用户目录的 .lufeng/uploads，
     * Docker 下由 LUFENG_STORAGE_DIR 指向容器持久化卷（如 /app/uploads）。
     */
    @Value("${lufeng.storage.dir:${user.home}/.lufeng/uploads}")
    private String storageDir;

    /**
     * Spring Boot 默认把 {@code classpath:/static/} 挂在根路径下，因此
     * {@code /static/images/default-avatar.png} 这种带前缀的地址是 404。
     * <p>
     * 数据库里存的是 {@code /static/...} 形式的前端可直接使用的相对路径，
     * 这里显式注册一份映射，让两种写法都能访问（换静态资源不必改数据）。
     * <p>
     * 第二个 location 指向上传目录，使聊天图片/视频（/static/yyyy/MM/uuid.xxx）
     * 也能同源回源，无需额外的 CDN/对象存储。classpath 优先，命中失败再回退到磁盘。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String fileLocation = "file:" + java.nio.file.Paths.get(storageDir).toAbsolutePath() + "/";
        registry.addResourceHandler("/static/**")
                .addResourceLocations("classpath:/static/", fileLocation);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/auth/refresh", "/api/v1/health");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
