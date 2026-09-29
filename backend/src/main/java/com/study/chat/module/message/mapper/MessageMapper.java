package com.study.chat.module.message.mapper;

import com.study.chat.module.message.entity.Message;
import com.study.chat.module.message.entity.MessageRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface MessageMapper {

    @Insert("""
            insert into message
                (msg_no, conversation_id, seq, sender_id, receiver_id, msg_type,
                 content, extra, client_msg_id, status, send_time)
            values
                (#{msgNo}, #{conversationId}, #{seq}, #{senderId}, #{receiverId}, #{msgType},
                 #{content}, #{extra}, #{clientMsgId}, #{status}, #{sendTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Select("select * from message where id = #{id} limit 1")
    Message findById(@Param("id") Long id);

    @Select("select * from message where msg_no = #{msgNo} limit 1")
    Message findByMsgNo(@Param("msgNo") String msgNo);

    /** 幂等查询：同一会话 + 同一 clientMsgId 只会有一条 */
    @Select("select * from message where conversation_id = #{conversationId} "
            + "and client_msg_id = #{clientMsgId} limit 1")
    Message findByClientMsgId(@Param("conversationId") Long conversationId,
                              @Param("clientMsgId") String clientMsgId);

    @Select("""
            select m.*, a.user_no sender_no, p.nickname sender_nickname, p.avatar sender_avatar
            from message m
            left join user_account a on a.id = m.sender_id
            left join user_profile p on p.user_id = m.sender_id
            where m.conversation_id = #{conversationId} and m.status <> 3
            order by m.seq desc
            limit #{limit}
            """)
    List<MessageRow> pageLatest(@Param("conversationId") Long conversationId, @Param("limit") int limit);

    /** 游标分页：向前翻页取更早的消息（多取 1 条用于判断 hasMore） */
    @Select("""
            select m.*, a.user_no sender_no, p.nickname sender_nickname, p.avatar sender_avatar
            from message m
            left join user_account a on a.id = m.sender_id
            left join user_profile p on p.user_id = m.sender_id
            where m.conversation_id = #{conversationId} and m.seq < #{cursor} and m.status <> 3
            order by m.seq desc
            limit #{limit}
            """)
    List<MessageRow> pageBackward(@Param("conversationId") Long conversationId,
                                  @Param("cursor") Long cursor,
                                  @Param("limit") int limit);

    /** 向后拉取更新的消息（用于补齐 seq 空洞） */
    @Select("""
            select m.*, a.user_no sender_no, p.nickname sender_nickname, p.avatar sender_avatar
            from message m
            left join user_account a on a.id = m.sender_id
            left join user_profile p on p.user_id = m.sender_id
            where m.conversation_id = #{conversationId} and m.seq > #{cursor} and m.status <> 3
            order by m.seq asc
            limit #{limit}
            """)
    List<MessageRow> pageForward(@Param("conversationId") Long conversationId,
                                 @Param("cursor") Long cursor,
                                 @Param("limit") int limit);

    /** 离线补拉：只要比 lastAckSeq 新的消息 */
    @Select("""
            select m.*, a.user_no sender_no, p.nickname sender_nickname, p.avatar sender_avatar
            from message m
            left join user_account a on a.id = m.sender_id
            left join user_profile p on p.user_id = m.sender_id
            where m.conversation_id = #{conversationId} and m.seq > #{afterSeq} and m.status <> 3
            order by m.seq asc
            limit #{limit}
            """)
    List<MessageRow> listAfter(@Param("conversationId") Long conversationId,
                               @Param("afterSeq") Long afterSeq,
                               @Param("limit") int limit);

    @Select("select ifnull(max(seq), 0) from message where conversation_id = #{conversationId}")
    Long maxSeq(@Param("conversationId") Long conversationId);

    @Update("update message set status = 2 where id = #{id} and status = 1")
    int recall(@Param("id") Long id);
}
