package com.study.chat.module.user.service;

import com.study.chat.module.auth.dto.UserVO;
import com.study.chat.module.user.dto.UpdateUserReq;

import java.util.List;

public interface UserService {

    UserVO me(long userId);

    UserVO update(long userId, UpdateUserReq req);

    List<UserVO> search(String keyword, int size);

    UserVO getByUserNo(String userNo);
}
