package com.study.chat.module.message.service.impl;

import com.study.chat.common.model.PageVO;
import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.common.util.IdGenerator;
import com.study.chat.infra.redis.RateLimiter;
import com.study.chat.infra.redis.SeqService;
import com.study.chat.module.auth.entity.UserAccount;
import com.study.chat.module.auth.entity.UserProfile;
import com.study.chat.module.auth.mapper.UserAccountMapper;
import com.study.chat.module.auth.mapper.UserProfileMapper;
import com.study.chat.module.conversation.entity.Conversation;
import com.study.chat.module.conversation.mapper.ConversationMapper;
import com.study.chat.module.conversation.mapper.ConversationMemberMapper;
import com.study.chat.module.conversation.service.ConversationService;
import com.study.chat.module.friend.service.FriendService;
import com.study.chat.module.message.dto.MessageVO;
import com.study.chat.module.message.dto.SendMessageReq;
import com.study.chat.module.message.dto.SyncReq;
import com.study.chat.module.message.entity.Message;
import com.study.chat.module.message.entity.MessageRow;
import com.study.chat.module.message.event.MessageRecalledEvent;
import com.study.chat.module.message.event.MessageSentEvent;
import com.study.chat.module.message.mapper.MessageMapper;
import com.study.chat.module.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_SYNC_SIZE = 200;
    private static final long RECALL_WINDOW_SECONDS = 120;

    private final MessageMapper messageMapper;
    private final ConversationMapper conversationMapper;
    private final ConversationMemberMapper conversationMemberMapper;
    private final ConversationService conversationService;
    private final FriendService friendService;
    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final SeqService seqService;
    private final RateLimiter rateLimiter;
    private final ApplicationEventPublisher publisher;

    @Value("${lufeng.security.send-message-rate-per-minute:60}")
    private long sendRatePerMinute;

    @Override
    @Transactional
    public MessageVO send(long senderId, String excludeDeviceId, SendMessageReq req) {
        Long conversationId = resolveConversationId(senderId, req);
        ConversationMemberHolder holder = requireSender(senderId, conversationId);

        if (req.getMsgType() != null && req.getMsgType() == 1
                && (req.getContent() == null || req.getContent().isBlank())) {
            throw new BizException(ResultCode.MESSAGE_EMPTY);
        }
        if (!rateLimiter.allow("send:" + senderId, Duration.ofMinutes(1), sendRatePerMinute)) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }

        // 幂等：重试复用同一 clientMsgId 时直接回放已有消息
        Message exist = messageMapper.findByClientMsgId(conversationId, req.getClientMsgId());
        if (exist != null) {
            return toVO(exist, true);
        }

        long seq = seqService.nextConversationSeq(conversationId);
        Message message = new Message();
        message.setMsgNo(IdGenerator.messageNo());
        message.setConversationId(conversationId);
        message.setSeq(seq);
        message.setSenderId(senderId);
        message.setReceiverId(holder.peerId());
        message.setMsgType(req.getMsgType() == null ? 1 : req.getMsgType());
        message.setContent(req.getContent());
        message.setExtra(req.getExtra());
        message.setClientMsgId(req.getClientMsgId());
        message.setStatus(1);
        message.setSendTime(LocalDateTime.now());

        try {
            messageMapper.insert(message);
        } catch (DuplicateKeyException e) {
            // 唯一索引 uk_conv_client_msg / uk_conv_seq 兜底
            Message duplicate = messageMapper.findByClientMsgId(conversationId, req.getClientMsgId());
            if (duplicate != null) {
                return toVO(duplicate, true);
            }
            throw e;
        }

        conversationMapper.updateLastMessage(conversationId, message.getId(), seq,
                buildPreview(message), message.getSendTime());

        List<Long> recipients = new ArrayList<>();
        for (Long memberId : holder.memberIds()) {
            if (memberId.equals(senderId)) {
                continue;
            }
            conversationMemberMapper.incrUnread(conversationId, memberId);
            recipients.add(memberId);
        }

        MessageVO vo = toVO(message, false);
        publisher.publishEvent(new MessageSentEvent(vo, recipients, excludeDeviceId));
        return vo;
    }

    @Override
    public PageVO<MessageVO> history(long userId, Long conversationId, Long cursor, int size) {
        conversationService.requireMember(conversationId, userId);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // 多取 1 条用于判断 hasMore
        List<MessageRow> rows = cursor == null
                ? messageMapper.pageLatest(conversationId, safeSize + 1)
                : messageMapper.pageBackward(conversationId, cursor, safeSize + 1);

        boolean hasMore = rows.size() > safeSize;
        List<MessageRow> page = hasMore ? rows.subList(0, safeSize) : rows;
        // 倒序取回后翻正，保证前端按时间正序渲染
        Collections.reverse(page);
        Long nextCursor = page.isEmpty() ? cursor : page.get(0).getSeq();
        return PageVO.of(page.stream().map(row -> toVO(row, false)).toList(), hasMore, nextCursor);
    }

    @Override
    @Transactional
    public List<MessageVO> sync(long userId, SyncReq req) {
        List<MessageVO> result = new ArrayList<>();
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return result;
        }
        for (SyncReq.SyncItem item : req.getItems()) {
            conversationService.requireMember(item.getConversationId(), userId);
            long afterSeq = item.getAfterSeq() == null ? 0L : item.getAfterSeq();
            List<MessageRow> rows = messageMapper.listAfter(item.getConversationId(), afterSeq, MAX_SYNC_SIZE);
            if (rows.isEmpty()) {
                continue;
            }
            long maxSeq = rows.get(rows.size() - 1).getSeq();
            conversationMemberMapper.markAck(item.getConversationId(), userId, maxSeq);
            rows.forEach(row -> result.add(toVO(row, false)));
        }
        return result;
    }

    @Override
    @Transactional
    public void recall(long userId, String msgNo, String excludeDeviceId) {
        Message message = messageMapper.findByMsgNo(msgNo);
        if (message == null) {
            throw new BizException(ResultCode.MESSAGE_NOT_FOUND);
        }
        if (!message.getSenderId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "只能撤回自己的消息");
        }
        long elapsed = Duration.between(
                message.getSendTime().atZone(ZoneId.systemDefault()).toInstant(),
                java.time.Instant.now()).getSeconds();
        if (elapsed > RECALL_WINDOW_SECONDS) {
            throw new BizException(ResultCode.RECALL_EXPIRED);
        }
        if (messageMapper.recall(message.getId()) == 0) {
            throw new BizException(ResultCode.MESSAGE_NOT_FOUND);
        }
        List<Long> recipients = conversationService.memberIds(message.getConversationId())
                .stream()
                .filter(id -> !id.equals(userId))
                .toList();
        publisher.publishEvent(new MessageRecalledEvent(message.getConversationId(), msgNo,
                message.getSeq(), userId, recipients, excludeDeviceId));
    }

    /**
     * 会话定位：优先用 conversationId；只给 peerId 时按单聊懒创建。
     * <p>
     * 添加好友不再预建会话，所以「好友列表里有他 / 会话列表里没有他」是常态，
     * 第一条消息发出时才真正建会话，避免空会话污染列表。
     */
    private Long resolveConversationId(long senderId, SendMessageReq req) {
        if (req.getConversationId() != null) {
            return req.getConversationId();
        }
        Long peerId = req.getPeerId();
        if (peerId == null) {
            throw new BizException(ResultCode.PARAM_ERROR, "conversationId 与 peerId 不能同时为空");
        }
        if (peerId.equals(senderId)) {
            throw new BizException(ResultCode.PARAM_ERROR, "不能给自己发消息");
        }
        if (!friendService.isFriend(senderId, peerId)) {
            throw new BizException(ResultCode.NOT_FRIEND, "好友关系已解除，无法发送消息");
        }
        return conversationService.createPrivate(senderId, peerId).getConversationId();
    }

    private ConversationMemberHolder requireSender(long senderId, Long conversationId) {
        if (conversationId == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        Conversation conversation = conversationService.requireConversation(conversationId);
        try {
            conversationService.requireMember(conversationId, senderId);
        } catch (BizException e) {
            // 单聊场景下「不是成员」几乎都是好友关系已解除（删除好友会解散会话），
            // 用更明确的文案，避免前端只显示一句「不是该会话成员」
            boolean isPrivate = conversation.getType() != null && conversation.getType() == 1;
            throw new BizException(isPrivate ? ResultCode.NOT_FRIEND : ResultCode.FORBIDDEN,
                    isPrivate ? "好友关系已解除，无法发送消息" : "不是该会话成员");
        }
        List<Long> memberIds = conversationService.memberIds(conversationId);
        Long peerId = null;
        if (conversation.getType() != null && conversation.getType() == 1) {
            peerId = memberIds.stream().filter(id -> !id.equals(senderId)).findFirst().orElse(null);
        }
        return new ConversationMemberHolder(memberIds, peerId);
    }

    private String buildPreview(Message message) {
        if (message.getContent() == null || message.getContent().isBlank()) {
            return switch (message.getMsgType() == null ? 1 : message.getMsgType()) {
                case 2 -> "[图片]";
                case 3 -> "[语音]";
                case 4 -> "[视频]";
                case 5 -> "[文件]";
                case 6 -> "[位置]";
                default -> "";
            };
        }
        String content = message.getContent().replaceAll("\\s+", " ").trim();
        return content.length() > 50 ? content.substring(0, 50) + "…" : content;
    }

    private MessageVO toVO(Message message, boolean duplicated) {
        UserAccount sender = userAccountMapper.findById(message.getSenderId());
        UserProfile profile = userProfileMapper.findByUserId(message.getSenderId());
        MessageRow row = new MessageRow();
        copy(message, row);
        if (sender != null) {
            row.setSenderNo(sender.getUserNo());
        }
        if (profile != null) {
            row.setSenderNickname(profile.getNickname());
            row.setSenderAvatar(profile.getAvatar());
        }
        return toVO(row, duplicated);
    }

    private MessageVO toVO(MessageRow row, boolean duplicated) {
        MessageVO vo = new MessageVO();
        vo.setMsgId(row.getId());
        vo.setMsgNo(row.getMsgNo());
        vo.setClientMsgId(row.getClientMsgId());
        vo.setConversationId(row.getConversationId());
        vo.setSeq(row.getSeq());
        vo.setSenderId(row.getSenderId());
        vo.setSenderNo(row.getSenderNo());
        vo.setSenderNickname(row.getSenderNickname());
        vo.setSenderAvatar(row.getSenderAvatar());
        vo.setMsgType(row.getMsgType());
        vo.setContent(row.getContent());
        vo.setExtra(row.getExtra());
        vo.setStatus(row.getStatus());
        vo.setSendTime(row.getSendTime() == null ? null
                : row.getSendTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        vo.setDuplicated(duplicated);
        return vo;
    }

    private void copy(Message from, MessageRow to) {
        to.setId(from.getId());
        to.setMsgNo(from.getMsgNo());
        to.setConversationId(from.getConversationId());
        to.setSeq(from.getSeq());
        to.setSenderId(from.getSenderId());
        to.setReceiverId(from.getReceiverId());
        to.setMsgType(from.getMsgType());
        to.setContent(from.getContent());
        to.setExtra(from.getExtra());
        to.setClientMsgId(from.getClientMsgId());
        to.setStatus(from.getStatus());
        to.setSendTime(from.getSendTime());
    }

    private record ConversationMemberHolder(List<Long> memberIds, Long peerId) {
    }
}
