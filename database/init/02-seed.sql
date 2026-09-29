-- ===================================================================
-- LufengApp 演示数据（仅在首次初始化数据卷时执行，生产环境请删除本文件）
-- 三个演示账号：zhangsan / lisi / wangwu，密码均为 123456
-- BCrypt(123456) = $2a$10$50o/R8A.HxQoE42GBH4e3O3AZAf.8WHeGfjm0Fcu/HBhn2aYjKG3G
-- ===================================================================

-- docker-entrypoint 对每个 .sql 文件是独立会话执行的：
-- 01-schema.sql 里的 SET NAMES 不会影响这里，必须重新声明，否则中文会被 latin1 双重编码。
SET NAMES utf8mb4;

USE lufeng_chat;

-- 1. 账户
INSERT INTO user_account (id, user_no, account, password_hash, password_algo, status, token_version, last_login_at)
VALUES
  (1, 'U00000001', 'zhangsan', '$2a$10$50o/R8A.HxQoE42GBH4e3O3AZAf.8WHeGfjm0Fcu/HBhn2aYjKG3G', 'bcrypt', 1, 1, NOW(3)),
  (2, 'U00000002', 'lisi',     '$2a$10$50o/R8A.HxQoE42GBH4e3O3AZAf.8WHeGfjm0Fcu/HBhn2aYjKG3G', 'bcrypt', 1, 1, NOW(3)),
  (3, 'U00000003', 'wangwu',   '$2a$10$50o/R8A.HxQoE42GBH4e3O3AZAf.8WHeGfjm0Fcu/HBhn2aYjKG3G', 'bcrypt', 1, 1, NOW(3))
ON DUPLICATE KEY UPDATE updated_at = NOW(3);

-- 2. 资料
INSERT INTO user_profile (user_id, nickname, avatar, gender, signature)
VALUES
  (1, '张三', '/static/images/default-avatar.png', 1, '码农一枚'),
  (2, '李四', '/static/images/default-avatar.png', 2, '热爱生活'),
  (3, '王五', '/static/images/default-avatar.png', 0, '新人报到')
ON DUPLICATE KEY UPDATE updated_at = NOW(3);

-- 3. 好友关系（双向两行）
INSERT INTO friend (user_id, friend_id, remark, source)
VALUES
  (1, 2, '李四同学', 'search'),
  (2, 1, '张三',     'search')
ON DUPLICATE KEY UPDATE updated_at = NOW(3);

-- 4. 单聊会话
INSERT INTO conversation (id, conversation_no, type, member_count, last_message_id, last_message_seq, last_message_preview, last_message_at)
VALUES
  -- conversation_no 必须与 IdGenerator.conversationNo("P1_2") 一致，否则重新加好友时会建出第二个会话
  (1, 'CP1_2', 1, 2, 3, 3, '你好，这是最后一条消息', NOW(3))
ON DUPLICATE KEY UPDATE updated_at = NOW(3);

INSERT INTO conversation_member (conversation_id, user_id, role, join_seq, last_ack_seq, last_read_seq, unread_count)
VALUES
  (1, 1, 0, 0, 3, 3, 0),
  (1, 2, 0, 0, 0, 0, 3)
ON DUPLICATE KEY UPDATE updated_at = NOW(3);

-- 5. 历史消息（seq 会话内递增）
INSERT INTO message (id, msg_no, conversation_id, seq, sender_id, receiver_id, msg_type, content, client_msg_id, status, send_time)
VALUES
  (1, 'M0000000001', 1, 1, 1, 2, 1, '你好，我是张三', 'seed-0001', 1, NOW(3)),
  (2, 'M0000000002', 1, 2, 2, 1, 1, '你好，我是李四', 'seed-0002', 1, NOW(3)),
  (3, 'M0000000003', 1, 3, 1, 2, 1, '很高兴认识你！', 'seed-0003', 1, NOW(3))
ON DUPLICATE KEY UPDATE updated_at = NOW(3);

-- 6. seq 号段初始化（Redis 降级用）
INSERT INTO seq_allocator (biz_key, current_seq)
VALUES ('conv:1', 3)
ON DUPLICATE KEY UPDATE current_seq = GREATEST(current_seq, VALUES(current_seq));
