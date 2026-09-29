package com.study.chat.module.conversation.mapper;

import com.study.chat.module.conversation.entity.Conversation;
import com.study.chat.module.conversation.entity.ConversationRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface ConversationMapper {

    @Select("select * from conversation where id = #{id} and deleted_at is null limit 1")
    Conversation findById(@Param("id") Long id);

    @Select("select * from conversation where conversation_no = #{conversationNo} and deleted_at is null limit 1")
    Conversation findByNo(@Param("conversationNo") String conversationNo);

    /**
     * 按成员对查找单聊会话。不依赖 conversation_no 的编码规则，历史数据同样能命中。
     */
    @Select("""
            select c.* from conversation c
            join conversation_member m1 on m1.conversation_id = c.id and m1.user_id = #{a}
            join conversation_member m2 on m2.conversation_id = c.id and m2.user_id = #{b}
            where c.type = 1 and c.deleted_at is null
            limit 1
            """)
    Conversation findPrivateBetween(@Param("a") Long a, @Param("b") Long b);

    @Insert("insert into conversation (conversation_no, type, member_count, status) "
            + "values (#{conversationNo}, #{type}, #{memberCount}, 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Conversation conversation);

    /**
     * 乐观更新会话摘要：旧消息到达时不会覆盖新摘要（last_message_seq < #{seq}）。
     */
    @Update("update conversation set last_message_id = #{messageId}, last_message_seq = #{seq}, "
            + "last_message_preview = #{preview}, last_message_at = #{time} "
            + "where id = #{conversationId} and last_message_seq < #{seq}")
    int updateLastMessage(@Param("conversationId") Long conversationId,
                          @Param("messageId") Long messageId,
                          @Param("seq") Long seq,
                          @Param("preview") String preview,
                          @Param("time") LocalDateTime time);

    @Select("""
            select c.id conversation_id, c.conversation_no, c.type, c.title, c.avatar,
                   c.last_message_id, c.last_message_seq, c.last_message_preview, c.last_message_at,
                   cm.unread_count, cm.last_read_seq, cm.last_ack_seq, cm.join_seq, cm.muted,
                   peer.id peer_id, peer.user_no peer_no, p.nickname peer_nickname, p.avatar peer_avatar
            from conversation_member cm
            join conversation c on c.id = cm.conversation_id and c.deleted_at is null
            left join conversation_member pm on pm.conversation_id = c.id and pm.user_id <> #{userId}
            left join user_account peer on peer.id = pm.user_id
            left join user_profile p on p.user_id = peer.id
            where cm.user_id = #{userId} and cm.is_deleted = 0
            order by c.last_message_at desc
            limit #{size} offset #{offset}
            """)
    List<ConversationRow> listByUser(@Param("userId") Long userId,
                                     @Param("size") int size,
                                     @Param("offset") int offset);
}
