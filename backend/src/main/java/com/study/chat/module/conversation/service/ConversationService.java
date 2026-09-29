package com.study.chat.module.conversation.service;

import com.study.chat.module.conversation.dto.ConversationVO;
import com.study.chat.module.conversation.entity.Conversation;
import com.study.chat.module.conversation.entity.ConversationMember;

import java.util.List;

public interface ConversationService {

    /**
     * 创建（或复用）单聊会话，并发安全、幂等。
     */
    ConversationVO createPrivate(long userId, Long targetUserId);

    /**
     * 解散两人的单聊会话：双方软退出（is_deleted=1），会话从双方列表消失，历史消息保留。
     * <p>
     * 删除好友时调用，保证「不是好友就不能再发消息」。重新成为好友时 {@link #createPrivate}
     * 会把成员重新激活，历史消息随之可见。
     */
    void dissolvePrivate(long userId, long otherUserId);

    List<ConversationVO> list(long userId, int page, int size);

    ConversationVO detail(long userId, Long conversationId);

    /**
     * 已读上报：返回清零后的未读数（固定为 0）
     */
    Long markRead(long userId, Long conversationId, Long lastReadSeq, String excludeDeviceId);

    void mute(long userId, Long conversationId, boolean muted);

    void delete(long userId, Long conversationId);

    /**
     * 校验会话成员身份，非成员直接抛无权限。
     */
    ConversationMember requireMember(Long conversationId, long userId);

    Conversation requireConversation(Long conversationId);

    List<Long> memberIds(Long conversationId);
}
