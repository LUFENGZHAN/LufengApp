package com.study.chat.module.friend.service.impl;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.common.util.IdGenerator;
import com.study.chat.infra.redis.PresenceService;
import com.study.chat.module.auth.mapper.UserAccountMapper;
import com.study.chat.module.auth.mapper.UserProfileMapper;
import com.study.chat.module.conversation.service.ConversationService;
import com.study.chat.module.friend.dto.ApplyFriendReq;
import com.study.chat.module.friend.dto.FriendRequestVO;
import com.study.chat.module.friend.dto.FriendVO;
import com.study.chat.module.friend.entity.Friend;
import com.study.chat.module.friend.entity.FriendRequest;
import com.study.chat.module.friend.entity.FriendRequestRow;
import com.study.chat.module.friend.entity.FriendRow;
import com.study.chat.module.friend.event.FriendNotifyEvent;
import com.study.chat.module.friend.mapper.FriendMapper;
import com.study.chat.module.friend.mapper.FriendRequestMapper;
import com.study.chat.module.friend.service.FriendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendServiceImpl implements FriendService {

    /** apply_msg 列长度上限 */
    private static final int APPLY_MSG_MAX = 255;

    /** 未填写留言时的默认文案 */
    private static final String DEFAULT_APPLY_MSG = "请求添加你为好友";

    private final FriendMapper friendMapper;
    private final FriendRequestMapper friendRequestMapper;
    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final ConversationService conversationService;
    private final PresenceService presenceService;
    private final ApplicationEventPublisher publisher;

    @Override
    @Transactional
    public Long apply(long userId, ApplyFriendReq req) {
        if (req.getToUserId() == null) {
            throw new BizException(ResultCode.PARAM_ERROR);
        }
        if (req.getToUserId().equals(userId)) {
            throw new BizException(ResultCode.FRIEND_SELF);
        }
        if (userAccountMapper.findById(req.getToUserId()) == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (friendMapper.countRelation(userId, req.getToUserId()) > 0) {
            throw new BizException(ResultCode.FRIEND_DUPLICATE, "对方已是好友");
        }
        FriendRequest pending = friendRequestMapper.findPending(userId, req.getToUserId());
        if (pending != null) {
            throw new BizException(ResultCode.FRIEND_DUPLICATE, "已发送过申请，请等待对方处理");
        }

        FriendRequest request = new FriendRequest();
        request.setRequestNo(IdGenerator.requestNo());
        request.setFromUserId(userId);
        request.setToUserId(req.getToUserId());
        request.setApplyMsg(normalizeApplyMsg(req.getApplyMsg()));
        friendRequestMapper.insert(request);
        publisher.publishEvent(new FriendNotifyEvent(req.getToUserId(), "friend_request",
                Map.of("requestId", request.getId(), "fromUserId", userId)));
        return request.getId();
    }

    @Override
    public List<FriendRequestVO> requests(long userId, String direction, Integer status) {
        int safeStatus = status == null ? 0 : status;
        List<FriendRequestRow> rows = "out".equalsIgnoreCase(direction)
                ? friendRequestMapper.listOutgoing(userId, safeStatus)
                : friendRequestMapper.listIncoming(userId, safeStatus);
        return rows.stream().map(this::toRequestVO).toList();
    }

    @Override
    @Transactional
    public void handle(long userId, Long requestId, String action) {
        boolean accept = "accept".equalsIgnoreCase(action);
        if (!accept && !"reject".equalsIgnoreCase(action)) {
            throw new BizException(ResultCode.PARAM_ERROR, "action 只能是 accept 或 reject");
        }
        FriendRequest request = friendRequestMapper.findById(requestId);
        if (request == null || request.getStatus() != 0) {
            throw new BizException(ResultCode.FRIEND_REQUEST_NOT_FOUND);
        }
        if (!request.getToUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权处理该申请");
        }
        int newStatus = accept ? 1 : 2;
        // CAS：并发重复点击时只有一次生效
        if (friendRequestMapper.handle(requestId, newStatus) == 0) {
            throw new BizException(ResultCode.FRIEND_REQUEST_NOT_FOUND, "申请已被处理");
        }
        publisher.publishEvent(new FriendNotifyEvent(request.getFromUserId(),
                accept ? "friend_accepted" : "friend_rejected",
                Map.of("requestId", requestId, "userId", userId)));

        if (accept) {
            addRelation(request.getFromUserId(), request.getToUserId());
            addRelation(request.getToUserId(), request.getFromUserId());
            // 注意：成为好友【不】预建会话，否则好友越多会话列表越脏。
            // 会话改为懒创建：第一条消息发出时由 MessageServiceImpl 用 peerId 现建。
        }
    }

    @Override
    public List<FriendVO> friends(long userId) {
        return friendMapper.listByUser(userId).stream().map(this::toFriendVO).toList();
    }

    @Override
    @Transactional
    public void delete(long userId, Long friendId) {
        if (friendId == null || friendMapper.countRelation(userId, friendId) == 0) {
            throw new BizException(ResultCode.NOT_FRIEND);
        }
        // 1. 双向解除好友关系
        friendMapper.deleteBoth(userId, friendId);
        // 2. 解散单聊会话：双方软退出，从此不能再收发消息（历史消息保留，重新加回好友后可见）
        conversationService.dissolvePrivate(userId, friendId);
        // 3. 作废双方之间「已同意」的历史申请，避免列表里残留无效记录
        friendRequestMapper.invalidateHandled(userId, friendId);
        // 4. 实时通知：对方要知道自己被删除，本人的其他端也要同步移除会话
        publisher.publishEvent(new FriendNotifyEvent(friendId, "friend_removed",
                Map.of("userId", userId)));
        publisher.publishEvent(new FriendNotifyEvent(userId, "friend_deleted",
                Map.of("userId", friendId)));
    }

    @Override
    public void remark(long userId, Long friendId, String remark) {
        Friend friend = friendMapper.findOne(userId, friendId);
        if (friend == null) {
            throw new BizException(ResultCode.NOT_FRIEND);
        }
        // 走精确更新：空串表示清空备注，与 addRelation 的「空值不覆盖」语义解耦
        friendMapper.updateRemark(userId, friendId, remark == null ? "" : remark.trim());
    }

    @Override
    public boolean isFriend(long userId, Long otherUserId) {
        return friendMapper.countRelation(userId, otherUserId) > 0;
    }

    /**
     * 留言归一化：null / 空白 → 默认文案；超长截断到列宽，避免 Data truncation。
     */
    private String normalizeApplyMsg(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_APPLY_MSG;
        }
        String trimmed = raw.trim();
        return trimmed.length() > APPLY_MSG_MAX ? trimmed.substring(0, APPLY_MSG_MAX) : trimmed;
    }

    private void addRelation(long userId, long friendId) {
        Friend friend = new Friend();
        friend.setUserId(userId);
        friend.setFriendId(friendId);
        friend.setRemark("");
        friend.setSource("search");
        friendMapper.insertIgnoreDuplicate(friend);
    }

    private FriendVO toFriendVO(FriendRow row) {
        FriendVO vo = new FriendVO();
        vo.setUserId(row.getFriendId());
        vo.setUserNo(row.getFriendNo());
        vo.setNickname(row.getNickname());
        vo.setAvatar(row.getAvatar());
        vo.setSignature(row.getSignature());
        vo.setRemark(row.getRemark());
        vo.setOnline(presenceService.isOnline(row.getFriendId()));
        return vo;
    }

    private FriendRequestVO toRequestVO(FriendRequestRow row) {
        FriendRequestVO vo = new FriendRequestVO();
        vo.setRequestId(row.getId());
        vo.setRequestNo(row.getRequestNo());
        vo.setUserId(row.getUserNo() == null ? null : row.getFromUserId());
        vo.setUserNo(row.getUserNo());
        vo.setNickname(row.getNickname());
        vo.setAvatar(row.getAvatar());
        vo.setApplyMsg(row.getApplyMsg());
        vo.setStatus(row.getStatus());
        vo.setCreatedAt(row.getCreatedAt());
        return vo;
    }
}
