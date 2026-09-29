package com.study.chat.common.util;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * 对外编号生成器：面向用户展示，不用于分片/排序（排序一律使用数据库 seq）。
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private IdGenerator() {
    }

    /** 用户编号：U + 10 位 */
    public static String userNo() {
        return "U" + random(10);
    }

    /** 会话编号：由调用方传入确定性后缀，保证并发创建唯一 */
    public static String conversationNo(String deterministicSuffix) {
        return "C" + deterministicSuffix;
    }

    /** 消息编号：M + 时间戳 + 6 位随机，便于人工定位时间 */
    public static String messageNo() {
        return "M" + Instant.now().toEpochMilli() + random(6);
    }

    /** 好友申请单号 */
    public static String requestNo() {
        return "R" + Instant.now().toEpochMilli() + random(4);
    }

    private static String random(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
