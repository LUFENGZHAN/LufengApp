package com.study.chat.common.result;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 全局错误码。
 * <p>
 * 分段约定：
 * <ul>
 *   <li>0     成功</li>
 *   <li>1xxxx 通用</li>
 *   <li>2xxxx 用户 / 认证</li>
 *   <li>3xxxx 好友 / 关系链</li>
 *   <li>4xxxx 会话 / 消息</li>
 * </ul>
 * Web 与 App 共用同一份错误码表：先按 HTTP 状态分流，再按 code 做细分提示。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success", HttpStatus.OK),

    /* ---------- 通用 ---------- */
    PARAM_ERROR(10001, "参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(10002, "未登录", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED(10003, "凭证已过期", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(10004, "无权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(10005, "资源不存在", HttpStatus.NOT_FOUND),
    TOO_MANY_REQUESTS(10006, "操作过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    SERVER_ERROR(10007, "服务异常", HttpStatus.INTERNAL_SERVER_ERROR),

    /* ---------- 用户 / 认证 ---------- */
    ACCOUNT_EXISTS(20001, "账号已存在", HttpStatus.BAD_REQUEST),
    BAD_CREDENTIALS(20002, "账号或密码错误", HttpStatus.BAD_REQUEST),
    ACCOUNT_DISABLED(20003, "账号已被禁用或锁定", HttpStatus.FORBIDDEN),
    REFRESH_INVALID(20004, "登录已失效，请重新登录", HttpStatus.UNAUTHORIZED),
    KICKED(20005, "账号已在其他设备登录", HttpStatus.UNAUTHORIZED),
    PASSWORD_WEAK(20006, "密码长度需为 6-32 位", HttpStatus.BAD_REQUEST),
    ACCOUNT_INVALID(20007, "账号格式不正确", HttpStatus.BAD_REQUEST),

    /* ---------- 好友 ---------- */
    FRIEND_SELF(30001, "不能添加自己", HttpStatus.BAD_REQUEST),
    FRIEND_DUPLICATE(30002, "已是好友或已发送过申请", HttpStatus.BAD_REQUEST),
    FRIEND_REQUEST_NOT_FOUND(30003, "好友申请不存在或已处理", HttpStatus.NOT_FOUND),
    NOT_FRIEND(30004, "非好友关系", HttpStatus.FORBIDDEN),

    /* ---------- 会话 / 消息 ---------- */
    CONVERSATION_NOT_FOUND(40001, "会话不存在", HttpStatus.NOT_FOUND),
    MESSAGE_EMPTY(40002, "消息内容为空", HttpStatus.BAD_REQUEST),
    MESSAGE_TOO_LARGE(40003, "消息体超出长度限制", HttpStatus.BAD_REQUEST),
    MESSAGE_NOT_FOUND(40005, "消息不存在", HttpStatus.NOT_FOUND),
    RECALL_EXPIRED(40006, "超过 2 分钟的消息不能撤回", HttpStatus.BAD_REQUEST);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ResultCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
