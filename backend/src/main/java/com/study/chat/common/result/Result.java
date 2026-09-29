package com.study.chat.common.result;

import lombok.Data;

/**
 * 统一响应体。Web 与 App 解析同一份结构。
 *
 * @param <T> 业务数据
 */
@Data
public class Result<T> {

    /**
     * 0 表示成功，非 0 为错误码（见 {@link ResultCode}）
     */
    private Integer code;

    private String message;

    private T data;

    /**
     * 链路追踪 ID，客户端报错时回传便于定位
     */
    private String requestId;

    private Long timestamp;

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this(code, message, data, null);
    }

    public Result(Integer code, String message, T data, String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.requestId = requestId;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(ResultCode resultCode) {
        return new Result<>(resultCode.getCode(), resultCode.getMessage(), null);
    }

    public static <T> Result<T> error(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
}
