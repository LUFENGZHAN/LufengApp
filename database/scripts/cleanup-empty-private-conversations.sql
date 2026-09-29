-- ---------------------------------------------------------------------------
-- 数据清理：隐藏「加好友时预建、但从未说过话」的空单聊会话
--
-- 背景：早期版本在同意好友申请时会立刻 createPrivate，于是每加一个好友
--       会话列表就多一条空会话（没有最后一条消息、也没聊过）。
--       新版已改为懒创建：成为好友不建会话，第一条消息发出时才建。
--       本脚本只用于清理**存量空会话**。
--
-- 语义：对「没有任何消息」的单聊会话，把双方成员置为已退出（is_deleted=1）。
--       会话行与成员行都保留，之后按 peerId 发第一条消息会自动 reopen 复用，
--       不会产生第二个会话，也不会丢历史。
--
-- 执行：docker exec -i lufeng-mysql mysql -uroot -proot123456 < database/scripts/cleanup-empty-private-conversations.sql
-- 建议：先跑第 1 段的 SELECT 确认影响范围，再放开第 2 段的 UPDATE。
-- ---------------------------------------------------------------------------

-- 1) 先看影响范围（被隐藏的会话 + 涉及成员行）
SELECT c.id AS conversation_id,
       c.conversation_no,
       cm.user_id,
       (SELECT COUNT(*) FROM message m WHERE m.conversation_id = c.id) AS msg_count
FROM conversation c
JOIN conversation_member cm ON cm.conversation_id = c.id AND cm.is_deleted = 0
WHERE c.type = 1
  AND c.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id);

-- 2) 执行清理（确认无误后取消注释执行）
-- UPDATE conversation c
-- JOIN conversation_member cm ON cm.conversation_id = c.id AND cm.is_deleted = 0
-- SET cm.is_deleted = 1, cm.unread_count = 0
-- WHERE c.type = 1
--   AND c.deleted_at IS NULL
--   AND NOT EXISTS (SELECT 1 FROM message m WHERE m.conversation_id = c.id);
