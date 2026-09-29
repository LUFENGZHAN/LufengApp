package com.study.chat.module.conversation.mapper;

import com.study.chat.module.conversation.entity.ConversationMember;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ConversationMemberMapper {

    @Select("select * from conversation_member where conversation_id = #{conversationId} "
            + "and user_id = #{userId} limit 1")
    ConversationMember findOne(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Select("select user_id from conversation_member where conversation_id = #{conversationId} and is_deleted = 0")
    List<Long> listMemberIds(@Param("conversationId") Long conversationId);

    @Insert("insert into conversation_member (conversation_id, user_id, role, join_seq, last_ack_seq, last_read_seq) "
            + "values (#{conversationId}, #{userId}, #{role}, #{joinSeq}, #{joinSeq}, #{joinSeq}) "
            + "on duplicate key update is_deleted = 0")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnoreDuplicate(ConversationMember member);

    /** 未读数原子自增 */
    @Update("update conversation_member set unread_count = unread_count + 1 "
            + "where conversation_id = #{conversationId} and user_id = #{userId}")
    int incrUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /** 已读上报：GREATEST 防止位点回退 */
    @Update("update conversation_member set last_read_seq = greatest(last_read_seq, #{seq}), unread_count = 0 "
            + "where conversation_id = #{conversationId} and user_id = #{userId}")
    int markRead(@Param("conversationId") Long conversationId,
                 @Param("userId") Long userId,
                 @Param("seq") Long seq);

    /** 离线补拉后推进 ack 位点 */
    @Update("update conversation_member set last_ack_seq = greatest(last_ack_seq, #{seq}) "
            + "where conversation_id = #{conversationId} and user_id = #{userId}")
    int markAck(@Param("conversationId") Long conversationId,
                @Param("userId") Long userId,
                @Param("seq") Long seq);

    @Update("update conversation_member set is_deleted = 1, unread_count = 0 "
            + "where conversation_id = #{conversationId} and user_id = #{userId}")
    int softDelete(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Update("update conversation_member set muted = #{muted} "
            + "where conversation_id = #{conversationId} and user_id = #{userId}")
    int updateMuted(@Param("conversationId") Long conversationId,
                    @Param("userId") Long userId,
                    @Param("muted") Integer muted);
}
