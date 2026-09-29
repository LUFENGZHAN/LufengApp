package com.study.chat.common.result;

import com.study.chat.common.trace.RequestIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.Set;

/**
 * 全局异常处理：把异常统一转成 {@link Result}，HTTP 状态与 body.code 保持一致。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> DEV_PROFILES = Set.of("dev", "local", "default");

    private final Environment environment;

    public GlobalExceptionHandler(Environment environment) {
        this.environment = environment;
    }

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBiz(BizException e) {
        return build(e.getResultCode().getHttpStatus(), e.getResultCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValid(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ResultCode.PARAM_ERROR.getMessage());
        return build(ResultCode.PARAM_ERROR, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<?>> handleMethodValid(HandlerMethodValidationException e) {
        return build(ResultCode.PARAM_ERROR, e.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<?>> handleNotReadable(HttpMessageNotReadableException e) {
        return build(ResultCode.PARAM_ERROR, "请求体格式不正确");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleUnexpected(Exception e) {
        log.error("[{}] 未处理异常", RequestIdUtil.get(), e);
        // 开发环境把根因回给客户端，省得翻日志；生产只返回笼统文案，避免泄漏实现细节
        String message = ResultCode.SERVER_ERROR.getMessage();
        if (DEV_PROFILES.contains(devProfile())) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            message = ResultCode.SERVER_ERROR.getMessage() + "：" + root;
        }
        return build(ResultCode.SERVER_ERROR, message);
    }

    private String devProfile() {
        String active = environment.getProperty("spring.profiles.active", "dev");
        return active == null ? "dev" : active;
    }

    private ResponseEntity<Result<?>> build(ResultCode code, String message) {
        return build(code.getHttpStatus(), code.getCode(), message);
    }

    private ResponseEntity<Result<?>> build(org.springframework.http.HttpStatus status, int code, String message) {
        Result<?> body = new Result<>(code, message, null, RequestIdUtil.get());
        return ResponseEntity.status(status).body(body);
    }
}
