package com.study.chat.module.auth.service;

import com.study.chat.module.auth.dto.ChangePasswordReq;
import com.study.chat.module.auth.dto.LoginReq;
import com.study.chat.module.auth.dto.RefreshReq;
import com.study.chat.module.auth.dto.RegisterReq;
import com.study.chat.module.auth.dto.TokenVO;
import com.study.chat.module.auth.dto.UserVO;

public interface AuthService {

    UserVO register(RegisterReq req, String clientIp);

    TokenVO login(LoginReq req, String clientIp);

    TokenVO refresh(RefreshReq req);

    /**
     * 登出当前设备，其他设备不受影响。
     */
    void logout(String authorization);

    UserVO me();

    /**
     * 改密码：成功后令牌版本递增，所有已登录设备下线。
     */
    void changePassword(ChangePasswordReq req);
}
