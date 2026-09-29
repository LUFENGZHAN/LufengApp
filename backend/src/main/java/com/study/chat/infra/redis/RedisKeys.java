package com.study.chat.infra.redis;

public final class RedisKeys {

    /** 会话内消息序号 */
    public static final String SEQ_CONV = "seq:conv:";

    /** 用户令牌版本，与 user_account.token_version 对齐 */
    public static final String TOKEN_VERSION = "auth:tv:";

    /** 已失效的 access token jti */
    public static final String JWT_BLACKLIST = "auth:blk:";

    /** 在线状态 Hash：field=deviceId */
    public static final String WS_ONLINE = "ws:online:";

    /** 幂等键 */
    public static final String IDEMPOTENT = "idem:";

    /** 分布式锁 */
    public static final String LOCK = "lock:";

    /** 限流窗口 */
    public static final String RATE = "rate:";

    /** 登录失败计数 */
    public static final String LOGIN_FAIL = "rate:login:";

    /** 跨节点 WS 投递主题 */
    public static final String WS_DISPATCH_TOPIC = "ws:dispatch";

    private RedisKeys() {
    }

    public static String seqConversation(long conversationId) {
        return SEQ_CONV + conversationId;
    }

    public static String tokenVersion(long userId) {
        return TOKEN_VERSION + userId;
    }

    public static String jwtBlacklist(String jti) {
        return JWT_BLACKLIST + jti;
    }

    public static String online(long userId) {
        return WS_ONLINE + userId;
    }

    public static String idempotent(String bizType, String bizKey) {
        return IDEMPOTENT + bizType + ":" + bizKey;
    }

    public static String lock(String key) {
        return LOCK + key;
    }

    public static String rate(String key) {
        return RATE + key;
    }

    public static String loginFail(String account) {
        return LOGIN_FAIL + account;
    }
}
