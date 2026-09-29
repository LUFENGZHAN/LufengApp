package com.study.chat.module.conversation.service.impl;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.common.util.IdGenerator;
import com.study.chat.infra.mapper.SeqAllocatorMapper;
import com.study.chat.infra.redis.DistributedLock;
import com.study.chat.infra.redis.PresenceService;
import com.study.chat.infra.redis.RedisKeys;
import com.study.chat.module.auth.entity.UserAccount;
import com.study.chat.module.auth.entity.UserProfile;
import com.study.chat.module.auth.mapper.UserAccountMapper;
import com.study.chat.module.auth.mapper.UserProfileMapper;
import com.study.chat.module.conversation.dto.ConversationVO;
import com.study.chat.module.conversation.entity.Conversation;
import com.study.chat.module.conversation.entity.ConversationMember;
import com.study.chat.module.conversation.entity.ConversationRow;
import com.study.chat.module.conversation.event.ReadEvent;
import com.study.chat.module.conversation.mapper.ConversationMapper;
import com.study.chat.module.conversation.mapper.ConversationMemberMapper;
import com.study.chat.module.conversation.service.ConversationService;
import com.study.chat.module.message.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final MessageMapper messageMapper;
    private final SeqAllocatorMapper seqAllocatorMapper;
    private final DistributedLock distributedLock;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher publisher;

    @Override
    @Transactional
    public ConversationVO createPrivate(long userId, Long targetUserId) {
        if (targetUserId == null || targetUserId.equals(userId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能与自己创建会话");
        }
        if (userAccountMapper.findById(targetUserId) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }

        // 确定性会话号：无论谁先发起、无论并发多少次，都只会有一个会话
        String conversationNo = IdGenerator.conversationNo(deterministicKey(userId, targetUserId));
        Conversation conversation = findExisting(userId, targetUserId, conversationNo);
        if (conversation != null) {
            return reopen(userId, targetUserId, conversation);
        }

        String lockKey = "conv:" + conversationNo;
        String token = distributedLock.tryLock(lockKey, Duration.ofSeconds(5));
        try {
            conversation = findExisting(userId, targetUserId, conversationNo);
            if (conversation != null) {
                return reopen(userId, targetUserId, conversation);
            }
            Conversation created = new Conversation();
            created.setConversationNo(conversationNo);
            created.setType(1);
            created.setMemberCount(2);
            try {
                conversationMapper.insert(created);
            } catch (DuplicateKeyException e) {
                // 唯一键兜底：并发下另一个事务已创建
                return detail(userId, conversationMapper.findByNo(conversationNo).getId());
            }
            seqAllocatorMapper.init(RedisKeys.seqConversation(created.getId()), 0L);
            addMember(created.getId(), userId, 0L);
            addMember(created.getId(), targetUserId, 0L);
            return detail(userId, created.getId());
        } finally {
            distributedLock.unlock(lockKey, token);
        }
    }

    /**
     * 会话已存在时重新激活双方成员：删除好友会把双方软退出，重新成为好友后需要恢复，
     * 否则会出现「好友列表里有他，但发消息报不是会话成员」的死局。
     * insertIgnoreDuplicate 的 on duplicate key 只置 is_deleted=0，ack/read 位点保持不变，历史消息可见。
     */
    private ConversationVO reopen(long userId, Long targetUserId, Conversation conversation) {
        addMember(conversation.getId(), userId, 0L);
        addMember(conversation.getId(), targetUserId, 0L);
        return detail(userId, conversation.getId());
    }

    @Override
    @Transactional
    public void dissolvePrivate(long userId, long otherUserId) {
        Conversation conversation = conversationMapper.findPrivateBetween(userId, otherUserId);
        if (conversation == null) {
            return;
        }
        conversationMemberMapper.softDelete(conversation.getId(), userId);
        conversationMemberMapper.softDelete(conversation.getId(), otherUserId);
    }

    @Override
    public List<ConversationVO> list(long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return conversationMapper.listByUser(userId, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public ConversationVO detail(long userId, Long conversationId) {
        Conversation conversation = requireConversation(conversationId);
        ConversationMember member = requireMember(conversationId, userId);
        Long peerId = memberIds(conversationId).stream()
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElse(null);
        return toVO(conversation, member, peerId);
    }

    @Override
    @Transactional
    public Long markRead(long userId, Long conversationId, Long lastReadSeq, String excludeDeviceId) {
        requireMember(conversationId, userId);
        conversationMemberMapper.markRead(conversationId, userId, lastReadSeq);
        List<Long> recipients = memberIds(conversationId).stream()
                .filter(id -> !id.equals(userId))
                .toList();
        publisher.publishEvent(
                new ReadEvent(conversationId, userId, lastReadSeq, recipients, excludeDeviceId));
        return 0L;
    }

    @Override
    public void mute(long userId, Long conversationId, boolean muted) {
        requireMember(conversationId, userId);
        conversationMemberMapper.updateMuted(conversationId, userId, muted ? 1 : 0);
    }

    @Override
    public void delete(long userId, Long conversationId) {
        requireMember(conversationId, userId);
        conversationMemberMapper.softDelete(conversationId, userId);
    }

    @Override
    public ConversationMember requireMember(Long conversationId, long userId) {
        ConversationMember member = conversationMemberMapper.findOne(conversationId, userId);
        if (member == null || Integer.valueOf(1).equals(member.getIsDeleted())) {
            throw new BizException(ResultCode.FORBIDDEN, "不是该会话成员");
        }
        return member;
    }

    @Override
    public Conversation requireConversation(Long conversationId) {
        Conversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null) {
            throw new BizException(ResultCode.CONVERSATION_NOT_FOUND);
        }
        return conversation;
    }

    @Override
    public List<Long> memberIds(Long conversationId) {
        return conversationMemberMapper.listMemberIds(conversationId);
    }

    private void addMember(Long conversationId, Long userId, long joinSeq) {
        ConversationMember member = new ConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setRole(0);
        member.setJoinSeq(joinSeq);
        conversationMemberMapper.insertIgnoreDuplicate(member);
    }

    /**
     * 先按确定性 conversation_no 找，找不到再按成员对兜底：
     * 历史数据的会话号可能不是确定性格式（如早期 seed 直接写死的 C00000001），
     * 只按 no 找会给同一对人建出第二个会话。
     */
    private Conversation findExisting(long userId, Long targetUserId, String conversationNo) {
        Conversation conversation = conversationMapper.findByNo(conversationNo);
        return conversation != null
                ? conversation
                : conversationMapper.findPrivateBetween(userId, targetUserId);
    }

    private static String deterministicKey(long a, long b) {
        return "P" + Math.min(a, b) + "_" + Math.max(a, b);
    }

    private ConversationVO toVO(ConversationRow row) {
        ConversationVO vo = new ConversationVO();
        vo.setConversationId(row.getConversationId());
        vo.setConversationNo(row.getConversationNo());
        vo.setType(row.getType());
        vo.setTitle(row.getTitle());
        vo.setAvatar(row.getAvatar());
        vo.setUnreadCount(row.getUnreadCount());
        vo.setLastReadSeq(row.getLastReadSeq());
        vo.setLastAckSeq(row.getLastAckSeq());
        vo.setMuted(row.getMuted());
        vo.setLastMessageAt(row.getLastMessageAt() == null ? null
                : row.getLastMessageAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());

        if (row.getPeerId() != null) {
            ConversationVO.PeerVO peer = new ConversationVO.PeerVO();
            peer.setUserId(row.getPeerId());
            peer.setUserNo(row.getPeerNo());
            peer.setNickname(row.getPeerNickname());
            peer.setAvatar(row.getPeerAvatar());
            peer.setOnline(presenceService.isOnline(row.getPeerId()));
            vo.setPeer(peer);
        }
        if (row.getLastMessageSeq() != null && row.getLastMessageSeq() > 0) {
            ConversationVO.LastMessageVO last = new ConversationVO.LastMessageVO();
            last.setMsgId(row.getLastMessageId());
            last.setSeq(row.getLastMessageSeq());
            last.setMsgType(1);
            last.setPreview(row.getLastMessagePreview());
            vo.setLastMessage(last);
        }
        return vo;
    }

    private ConversationVO toVO(Conversation conversation, ConversationMember member, Long peerId) {
        ConversationRow row = new ConversationRow();
        row.setConversationId(conversation.getId());
        row.setConversationNo(conversation.getConversationNo());
        row.setType(conversation.getType());
        row.setTitle(conversation.getTitle());
        row.setAvatar(conversation.getAvatar());
        row.setLastMessageId(conversation.getLastMessageId());
        row.setLastMessageSeq(conversation.getLastMessageSeq() == null ? 0L : conversation.getLastMessageSeq());
        row.setLastMessagePreview(conversation.getLastMessagePreview());
        row.setLastMessageAt(conversation.getLastMessageAt());
        row.setUnreadCount(member.getUnreadCount() == null ? 0L : member.getUnreadCount().longValue());
        row.setLastReadSeq(member.getLastReadSeq());
        row.setLastAckSeq(member.getLastAckSeq());
        row.setMuted(member.getMuted());
        if (peerId != null) {
            row.setPeerId(peerId);
            UserAccount peer = userAccountMapper.findById(peerId);
            UserProfile profile = userProfileMapper.findByUserId(peerId);
            if (peer != null) {
                row.setPeerNo(peer.getUserNo());
            }
            if (profile != null) {
                row.setPeerNickname(profile.getNickname());
                row.setPeerAvatar(profile.getAvatar());
            }
        }
        return toVO(row);
    }

    @SuppressWarnings("unused")
    private Long currentSeq(Long conversationId) {
        Long maxSeq = messageMapper.maxSeq(conversationId);
        return maxSeq == null ? 0L : maxSeq;
    }

    @SuppressWarnings("unused")
    private List<Long> emptyIfNull(List<Long> list) {
        return list == null ? new ArrayList<>() : list;
    }
}
