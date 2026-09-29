-- ===================================================================
-- LufengApp 在线聊天系统 · 核心表结构 (MySQL 8.0 / InnoDB / utf8mb4)
-- 说明：
--   1. 所有金额/时间无关业务时间统一 DATETIME(3)（毫秒），由服务端生成
--   2. 逻辑删除统一 deleted_at 字段，不使用物理删除（消息可溯源）
--   3. 消息查询主链路走 (conversation_id, seq) 联合索引，支持游标分页
--   4. 幂等性由唯一索引兜底，而不是靠应用层判断
-- ===================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

CREATE DATABASE IF NOT EXISTS lufeng_chat
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE lufeng_chat;

-- ------------------------------------------------------------------
-- 1. 用户账户表（登录凭证）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_account (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_no          VARCHAR(32)  NOT NULL                COMMENT '用户编号（对外暴露，不可变）',
    account          VARCHAR(64)  NOT NULL                COMMENT '登录账号',
    phone            VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    email            VARCHAR(128) DEFAULT NULL            COMMENT '邮箱',
    password_hash    VARCHAR(128) NOT NULL                COMMENT '密码摘要（BCrypt）',
    password_algo    VARCHAR(20)  NOT NULL DEFAULT 'bcrypt' COMMENT '加密算法，便于日后平滑升级',
    status           TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0禁用 1正常 2锁定',
    token_version    INT UNSIGNED NOT NULL DEFAULT 1      COMMENT '令牌版本，+1 后该用户所有已签发 JWT 失效',
    last_login_at    DATETIME(3)  DEFAULT NULL            COMMENT '最后登录时间',
    last_login_ip    VARCHAR(64)  DEFAULT NULL            COMMENT '最后登录 IP',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at       DATETIME(3)  DEFAULT NULL            COMMENT '逻辑删除时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_no (user_no),
    UNIQUE KEY uk_account (account),
    UNIQUE KEY uk_phone   (phone),
    UNIQUE KEY uk_email   (email),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户账户表';


-- ------------------------------------------------------------------
-- 2. 用户资料表（与账户分离：账户高频鉴权，资料高频读取）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_profile (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL                COMMENT 'user_account.id',
    nickname   VARCHAR(64)  NOT NULL DEFAULT ''        COMMENT '昵称',
    avatar     VARCHAR(512) NOT NULL DEFAULT ''        COMMENT '头像 URL',
    gender     TINYINT      NOT NULL DEFAULT 0         COMMENT '0未知 1男 2女',
    signature  VARCHAR(255) NOT NULL DEFAULT ''        COMMENT '个性签名',
    region     VARCHAR(64)  DEFAULT NULL               COMMENT '地区',
    birthday   DATE         DEFAULT NULL               COMMENT '生日',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_nickname (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '用户资料表';


-- ------------------------------------------------------------------
-- 3. 设备会话表（多端在线 / refresh token 轮换 / 强制下线）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_device_session (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id            BIGINT UNSIGNED NOT NULL            COMMENT 'user_account.id',
    device_id          VARCHAR(64)  NOT NULL               COMMENT '客户端设备唯一ID（App 安装后生成）',
    device_type        VARCHAR(16)  NOT NULL               COMMENT 'web / android / ios / desktop',
    device_name        VARCHAR(128) DEFAULT NULL           COMMENT '设备名，如 XiaoMi 14',
    refresh_token_hash CHAR(64)     NOT NULL               COMMENT 'sha256hex(refreshToken)，不存明文',
    refresh_expires_at DATETIME(3)  NOT NULL               COMMENT 'refresh token 过期时间',
    access_jti         VARCHAR(64)  DEFAULT NULL           COMMENT '当前 access token 的 jti，登出时拉黑',
    client_ip          VARCHAR(64)  DEFAULT NULL,
    status             TINYINT      NOT NULL DEFAULT 1     COMMENT '1有效 0失效（登出/被踢）',
    login_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_active_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_device (user_id, device_id),
    KEY idx_refresh_hash (refresh_token_hash),
    KEY idx_expires (refresh_expires_at),
    KEY idx_user_status (user_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '设备会话表（多端登录）';


-- ------------------------------------------------------------------
-- 4. 好友关系表（双向存两行，便于单侧查询走索引）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS friend (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL            COMMENT '本人',
    friend_id  BIGINT UNSIGNED NOT NULL            COMMENT '好友',
    remark     VARCHAR(64)  NOT NULL DEFAULT ''    COMMENT '备注名',
    source     VARCHAR(32)  DEFAULT NULL           COMMENT '来源：search/qrcode/phone',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_friend (user_id, friend_id),
    KEY idx_friend_id (friend_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '好友关系表';


-- ------------------------------------------------------------------
-- 5. 好友申请表
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS friend_request (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    request_no   VARCHAR(32)  NOT NULL            COMMENT '申请单号',
    from_user_id BIGINT UNSIGNED NOT NULL,
    to_user_id   BIGINT UNSIGNED NOT NULL,
    apply_msg    VARCHAR(255) NOT NULL DEFAULT '' COMMENT '申请留言',
    status       TINYINT      NOT NULL DEFAULT 0  COMMENT '0待处理 1已同意 2已拒绝 3已忽略',
    handled_at   DATETIME(3)  DEFAULT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_no (request_no),
    KEY idx_to_status (to_user_id, status, id),
    KEY idx_from_to   (from_user_id, to_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '好友申请表';


-- ------------------------------------------------------------------
-- 6. 会话表（单聊 / 群聊）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation (
    id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_no      VARCHAR(32)  NOT NULL                COMMENT '会话号（对外）',
    type                 TINYINT      NOT NULL DEFAULT 1      COMMENT '1单聊 2群聊 3系统通知',
    owner_id             BIGINT UNSIGNED DEFAULT NULL         COMMENT '群主；单聊为空',
    title                VARCHAR(128) DEFAULT NULL            COMMENT '群名；单聊为空',
    avatar               VARCHAR(512) DEFAULT NULL,
    member_count         INT UNSIGNED NOT NULL DEFAULT 0,
    last_message_id      BIGINT UNSIGNED DEFAULT NULL         COMMENT '最后一条消息 ID',
    last_message_seq     BIGINT UNSIGNED NOT NULL DEFAULT 0   COMMENT '最后一条消息 seq',
    last_message_preview VARCHAR(255) NOT NULL DEFAULT ''     COMMENT '会话列表摘要',
    last_message_at      DATETIME(3)  DEFAULT NULL,
    status               TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 0解散',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at           DATETIME(3)  DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_no (conversation_no),
    KEY idx_last_message_at (last_message_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话表';


-- ------------------------------------------------------------------
-- 7. 会话成员表（未读 / 已读位点 / 离线补拉位点 / 多端同步）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS conversation_member (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL            COMMENT 'conversation.id',
    user_id        BIGINT UNSIGNED NOT NULL,
    role           TINYINT      NOT NULL DEFAULT 0      COMMENT '0成员 1群主 2管理员',
    join_seq       BIGINT UNSIGNED NOT NULL DEFAULT 0   COMMENT '加入时位点，早于该 seq 的消息不可见',
    last_ack_seq   BIGINT UNSIGNED NOT NULL DEFAULT 0   COMMENT '已确认接收的最大 seq（离线补拉起点）',
    last_read_seq  BIGINT UNSIGNED NOT NULL DEFAULT 0   COMMENT '已读位点',
    unread_count   INT UNSIGNED NOT NULL DEFAULT 0      COMMENT '未读数（原子自增）',
    muted          TINYINT      NOT NULL DEFAULT 0      COMMENT '免打扰',
    is_deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否删除该会话',
    joined_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_conv_user (conversation_id, user_id),
    KEY idx_user_updated (user_id, updated_at),
    KEY idx_user_unread (user_id, unread_count)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '会话成员表';


-- ------------------------------------------------------------------
-- 8. 消息主表（核心：持久化、幂等、游标分页）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    msg_no          VARCHAR(32)  NOT NULL                COMMENT '消息号（对外，UUID/雪花）',
    conversation_id BIGINT UNSIGNED NOT NULL,
    seq             BIGINT UNSIGNED NOT NULL             COMMENT '会话内单调递增序号',
    sender_id       BIGINT UNSIGNED NOT NULL,
    receiver_id     BIGINT UNSIGNED DEFAULT NULL         COMMENT '单聊接收者，冗余便于统计',
    msg_type        TINYINT      NOT NULL DEFAULT 1      COMMENT '1文本 2图片 3语音 4视频 5文件 6位置 10系统 11撤回',
    content         TEXT                                 COMMENT '文本正文 / Markdown',
    extra           JSON         DEFAULT NULL            COMMENT '扩展：图片宽高、文件 URL、语音时长、@列表',
    client_msg_id   VARCHAR(64)  DEFAULT NULL            COMMENT '客户端幂等 ID',
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '1正常 2已撤回 3已删除',
    send_time       DATETIME(3)  NOT NULL                COMMENT '服务端接收时间（排序与分页基准）',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_no (msg_no),
    UNIQUE KEY uk_conv_seq (conversation_id, seq),
    UNIQUE KEY uk_conv_client_msg (conversation_id, client_msg_id), -- 幂等兜底
    KEY idx_conv_seq (conversation_id, seq),
    KEY idx_sender_time (sender_id, send_time),
    KEY idx_send_time (send_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息表';

-- 数据量大时的分区示例（可选，按月分区，上线前评估）：
-- ALTER TABLE message PARTITION BY RANGE COLUMNS (send_time) (
--     PARTITION p2026_09 VALUES LESS THAN ('2026-10-01'),
--     PARTITION p2026_10 VALUES LESS THAN ('2026-11-01'),
--     PARTITION pmax     VALUES LESS THAN (MAXVALUE)
-- );


-- ------------------------------------------------------------------
-- 9. 消息回执表（可选：送达/已读回执；量大时可只保留 30 天）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message_receipt (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    msg_id          BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    seq             BIGINT UNSIGNED NOT NULL,
    status          TINYINT      NOT NULL DEFAULT 1      COMMENT '1已送达 2已读',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user (msg_id, user_id),
    KEY idx_user_conv_seq (user_id, conversation_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '消息回执表';


-- ------------------------------------------------------------------
-- 10. 幂等记录表（注册、加好友、消息发送等写操作的通用去重）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_record (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_type    VARCHAR(32)  NOT NULL                COMMENT '业务类型：send_msg/register/apply_friend',
    biz_key     VARCHAR(128) NOT NULL                COMMENT '去重键：uid+clientMsgId 等',
    result_json JSON         DEFAULT NULL            COMMENT '首次执行结果，命中幂等时直接回放',
    expire_at   DATETIME(3)  NOT NULL                COMMENT '过期时间，定期清理',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz (biz_type, biz_key),
    KEY idx_expire (expire_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '幂等记录表';


-- ------------------------------------------------------------------
-- 11. 号段分配表（Redis 不可用时的 seq 降级方案）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS seq_allocator (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    biz_key     VARCHAR(64)  NOT NULL                COMMENT '如 conv:10001',
    current_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version     BIGINT UNSIGNED NOT NULL DEFAULT 0   COMMENT '乐观锁版本号',
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_key (biz_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '号段分配表（Redis 降级用）';


-- ------------------------------------------------------------------
-- 12. 群聊扩展表（二期可选，一期先支持单聊）
-- ------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS chat_group (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    group_no   VARCHAR(32)  NOT NULL,
    owner_id   BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(128) NOT NULL DEFAULT '',
    avatar     VARCHAR(512) DEFAULT NULL,
    notice     VARCHAR(512) DEFAULT NULL,
    member_limit INT UNSIGNED NOT NULL DEFAULT 500,
    status     TINYINT      NOT NULL DEFAULT 1,
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_group_no (group_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '群表（二期）';
