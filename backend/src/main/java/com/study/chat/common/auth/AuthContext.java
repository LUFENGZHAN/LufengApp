package com.study.chat.common.auth;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;

/**
 * 登录用户上下文持有者（线程隔离，请求结束必须 clear）。
 */
public final class AuthContext {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static long userId() {
        UserContext context = HOLDER.get();
        if (context == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return context.userId();
    }

    public static String deviceId() {
        UserContext context = HOLDER.get();
        return context == null ? null : context.deviceId();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
