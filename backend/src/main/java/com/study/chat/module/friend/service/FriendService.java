package com.study.chat.module.friend.service;

import com.study.chat.module.friend.dto.ApplyFriendReq;
import com.study.chat.module.friend.dto.FriendRequestVO;
import com.study.chat.module.friend.dto.FriendVO;

import java.util.List;

public interface FriendService {

    /**
     * 发送好友申请，返回申请单 ID（重复申请返回已有单号）
     */
    Long apply(long userId, ApplyFriendReq req);

    /**
     * @param direction in：收到的；out：发出的
     */
    List<FriendRequestVO> requests(long userId, String direction, Integer status);

    void handle(long userId, Long requestId, String action);

    List<FriendVO> friends(long userId);

    void delete(long userId, Long friendId);

    void remark(long userId, Long friendId, String remark);

    boolean isFriend(long userId, Long otherUserId);
}
