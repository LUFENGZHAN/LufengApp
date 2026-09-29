package com.study.chat.module.user.service.impl;

import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.module.auth.dto.UserVO;
import com.study.chat.module.auth.entity.UserAccount;
import com.study.chat.common.constant.Defaults;
import com.study.chat.module.auth.entity.UserProfile;
import com.study.chat.module.auth.mapper.UserAccountMapper;
import com.study.chat.module.auth.mapper.UserProfileMapper;
import com.study.chat.module.auth.support.UserViewFactory;
import com.study.chat.module.user.dto.UpdateUserReq;
import com.study.chat.module.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserViewFactory userViewFactory;

    @Override
    public UserVO me(long userId) {
        return load(userId);
    }

    @Override
    @Transactional
    public UserVO update(long userId, UpdateUserReq req) {
        UserProfile profile = userProfileMapper.findByUserId(userId);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(userId);
            profile.setGender(0);
            profile.setAvatar(Defaults.AVATAR);
            profile.setSignature("");
            userProfileMapper.insert(profile);
        }
        profile.setUserId(userId);
        profile.setNickname(req.getNickname());
        profile.setAvatar(req.getAvatar());
        profile.setGender(req.getGender());
        profile.setSignature(req.getSignature());
        // 全空请求会拼出「update user_profile where user_id = ?」的非法 SQL，直接跳过
        if (profile.getNickname() != null || profile.getAvatar() != null
                || profile.getGender() != null || profile.getSignature() != null) {
            userProfileMapper.updateSelective(profile);
        }
        return load(userId);
    }

    @Override
    public List<UserVO> search(String keyword, int size) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        int safeSize = Math.min(Math.max(size, 1), 50);
        List<UserVO> result = new ArrayList<>();
        for (UserAccount account : userAccountMapper.search(keyword.trim(), safeSize)) {
            result.add(userViewFactory.toVO(account, userProfileMapper.findByUserId(account.getId())));
        }
        return result;
    }

    @Override
    public UserVO getByUserNo(String userNo) {
        UserAccount account = userAccountMapper.findByUserNo(userNo);
        if (account == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return userViewFactory.toVO(account, userProfileMapper.findByUserId(account.getId()));
    }

    private UserVO load(long userId) {
        UserAccount account = userAccountMapper.findById(userId);
        if (account == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userViewFactory.toVO(account, userProfileMapper.findByUserId(userId));
    }
}
