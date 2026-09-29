package com.study.chat.common.result;

import lombok.Getter;

/**
 * 业务异常：承载确定的错误码，由 {@link GlobalExceptionHandler} 转成统一响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final ResultCode resultCode;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BizException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    public int getCode() {
        return resultCode.getCode();
    }
}
