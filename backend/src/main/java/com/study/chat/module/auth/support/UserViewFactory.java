package com.study.chat.module.auth.support;

import com.study.chat.infra.redis.PresenceService;
import com.study.chat.module.auth.dto.UserVO;
import com.study.chat.module.auth.entity.UserAccount;
import com.study.chat.module.auth.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 账户 + 资料 → 用户视图，供认证、好友、会话等模块复用。
 */
@Component
@RequiredArgsConstructor
public class UserViewFactory {

    private final PresenceService presenceService;

    public UserVO toVO(UserAccount account, UserProfile profile) {
        UserVO vo = new UserVO();
        vo.setUserId(account.getId());
        vo.setUserNo(account.getUserNo());
        vo.setAccount(account.getAccount());
        if (profile != null) {
            vo.setNickname(profile.getNickname());
            vo.setAvatar(profile.getAvatar());
            vo.setGender(profile.getGender());
            vo.setSignature(profile.getSignature());
        }
        vo.setOnline(presenceService.isOnline(account.getId()));
        return vo;
    }

    public UserVO toVO(UserAccount account) {
        return toVO(account, null);
    }
}
