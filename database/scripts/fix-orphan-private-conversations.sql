-- ---------------------------------------------------------------------------
-- 数据修复：解散「好友关系已解除但仍活跃」的单聊会话
--
-- 背景：早期版本的「删除好友」只删除 friend 表的两行，没有同步解散会话，
--       导致双方已不是好友、会话却仍在列表里且还能收发消息。
--       新版本已在代码里处理（FriendServiceImpl.delete → ConversationService.dissolvePrivate），
--       本脚本只用于修复**存量脏数据**。
--
-- 语义：单聊会话要求双方互为好友；任一侧关系缺失就把双方成员置为已退出
--      （is_deleted=1、未读清零）。会话与历史消息均保留，重新加回好友后自动恢复可见。
--
-- 执行：docker exec -i lufeng-mysql mysql -uroot -proot123456 < database/scripts/fix-orphan-private-conversations.sql
-- 建议：先跑 SELECT 版本确认影响范围，再执行 UPDATE。
-- ---------------------------------------------------------------------------

-- 1) 先看影响范围
SELECT c.id AS conversation_id, cm.user_id, pm.user_id AS peer_id
FROM conversation_member cm
JOIN conversation c ON c.id = cm.conversation_id AND c.type = 1 AND c.deleted_at IS NULL
JOIN conversation_member pm ON pm.conversation_id = c.id AND pm.user_id <> cm.user_id
LEFT JOIN friend f ON f.user_id = cm.user_id AND f.friend_id = pm.user_id
WHERE cm.is_deleted = 0 AND f.id IS NULL;

-- 2) 执行修复
UPDATE conversation_member cm
JOIN conversation c ON c.id = cm.conversation_id AND c.type = 1 AND c.deleted_at IS NULL
JOIN conversation_member pm ON pm.conversation_id = c.id AND pm.user_id <> cm.user_id
LEFT JOIN friend f ON f.user_id = cm.user_id AND f.friend_id = pm.user_id
SET cm.is_deleted = 1, cm.unread_count = 0
WHERE cm.is_deleted = 0 AND f.id IS NULL;
