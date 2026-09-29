package com.study.chat.common.constant;

/**
 * 全局默认值常量。
 * <p>
 * 约定：凡是数据库里声明为 {@code NOT NULL} 的字符串列，应用层写入前都必须有非空兜底，
 * 因为 MySQL 的列级 {@code DEFAULT} 对「显式插入 NULL」无效（会抛 1048）。
 */
public final class Defaults {

    /** 默认头像（后端静态资源，见 WebConfig 的 /static/** 映射） */
    public static final String AVATAR = "/static/images/default-avatar.png";

    private Defaults() {
    }
}
