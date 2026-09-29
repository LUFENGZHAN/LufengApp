package com.study.chat.module.auth.service.impl;

import com.study.chat.common.auth.AuthContext;
import com.study.chat.common.auth.JwtClaims;
import com.study.chat.common.auth.JwtTokenProvider;
import com.study.chat.common.auth.UserContext;
import com.study.chat.common.result.BizException;
import com.study.chat.common.result.ResultCode;
import com.study.chat.common.util.HashUtil;
import com.study.chat.common.util.IdGenerator;
import com.study.chat.infra.redis.PresenceService;
import com.study.chat.infra.redis.RateLimiter;
import com.study.chat.infra.redis.RedisKeys;
import com.study.chat.infra.redis.TokenBlacklistService;
import com.study.chat.infra.redis.TokenVersionService;
import com.study.chat.module.auth.dto.ChangePasswordReq;
import com.study.chat.module.auth.dto.LoginReq;
import com.study.chat.module.auth.dto.RefreshReq;
import com.study.chat.module.auth.dto.RegisterReq;
import com.study.chat.module.auth.dto.TokenVO;
import com.study.chat.module.auth.dto.UserVO;
import com.study.chat.module.auth.entity.UserAccount;
import com.study.chat.module.auth.entity.UserDeviceSession;
import com.study.chat.common.constant.Defaults;
import com.study.chat.module.auth.entity.UserProfile;
import com.study.chat.module.auth.mapper.UserAccountMapper;
import com.study.chat.module.auth.mapper.UserDeviceSessionMapper;
import com.study.chat.module.auth.mapper.UserProfileMapper;
import com.study.chat.module.auth.service.AuthService;
import com.study.chat.module.auth.support.UserViewFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String PASSWORD_ALGO = "bcrypt";
    private static final String REFRESH_REUSED_PREFIX = "auth:refresh:used:";

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserDeviceSessionMapper deviceSessionMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;
    private final TokenVersionService tokenVersionService;
    private final RateLimiter rateLimiter;
    private final PresenceService presenceService;
    private final UserViewFactory userViewFactory;
    private final StringRedisTemplate redis;

    @Value("${lufeng.security.login-max-fail:5}")
    private int loginMaxFail;

    @Value("${lufeng.security.login-lock-seconds:300}")
    private long loginLockSeconds;

    @Override
    @Transactional
    public UserVO register(RegisterReq req, String clientIp) {
        if (!rateLimiter.allow("register:" + clientIp, Duration.ofHours(1), 20)) {
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }
        if (userAccountMapper.findByAccount(req.getAccount()) != null) {
            throw new BizException(ResultCode.ACCOUNT_EXISTS);
        }

        UserAccount account = new UserAccount();
        account.setUserNo(IdGenerator.userNo());
        account.setAccount(req.getAccount());
        account.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        account.setPasswordAlgo(PASSWORD_ALGO);
        account.setStatus(1);
        account.setTokenVersion(1);
        userAccountMapper.insert(account);

        UserProfile profile = new UserProfile();
        profile.setUserId(account.getId());
        profile.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getAccount() : req.getNickname());
        profile.setGender(0);
        // avatar / signature 是 NOT NULL 列，必须显式给非空初值
        profile.setAvatar(Defaults.AVATAR);
        profile.setSignature("");
        userProfileMapper.insert(profile);

        return userViewFactory.toVO(account, profile);
    }

    @Override
    @Transactional
    public TokenVO login(LoginReq req, String clientIp) {
        String failKey = RedisKeys.loginFail(req.getAccount());
        if (!rateLimiter.allow(failKey, Duration.ofSeconds(loginLockSeconds), loginMaxFail)) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED, "登录失败次数过多，请稍后重试");
        }

        UserAccount account = userAccountMapper.findByAccount(req.getAccount());
        if (account == null || !passwordEncoder.matches(req.getPassword(), account.getPasswordHash())) {
            throw new BizException(ResultCode.BAD_CREDENTIALS);
        }
        if (account.getStatus() == null || account.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }
        rateLimiter.reset(failKey);

        String refreshToken = jwtTokenProvider.createRefreshToken();
        UserDeviceSession session = new UserDeviceSession();
        session.setUserId(account.getId());
        session.setDeviceId(req.getDeviceId());
        session.setDeviceType(req.getDeviceType() == null ? "web" : req.getDeviceType());
        session.setRefreshTokenHash(HashUtil.sha256Hex(refreshToken));
        session.setRefreshExpiresAt(LocalDateTime.ofInstant(jwtTokenProvider.refreshExpiresAt(), ZoneId.systemDefault()));
        session.setClientIp(clientIp);
        deviceSessionMapper.upsert(session);

        String accessToken = jwtTokenProvider.createAccessToken(
                account.getId(), account.getUserNo(), req.getDeviceId(), account.getTokenVersion());
        deviceSessionMapper.updateAccessJti(account.getId(), req.getDeviceId(),
                jwtTokenProvider.parse(accessToken).jti());

        userAccountMapper.updateLastLogin(account.getId(), LocalDateTime.now(), clientIp);
        return buildTokenVO(account, accessToken, refreshToken);
    }

    @Override
    @Transactional
    public TokenVO refresh(RefreshReq req) {
        String hash = HashUtil.sha256Hex(req.getRefreshToken());
        UserDeviceSession session = deviceSessionMapper.findByRefreshHash(hash);

        if (session == null || session.getStatus() == null || session.getStatus() != 1) {
            // 旧 refresh 被重复使用 → 判定为令牌泄露，吊销该用户全部设备会话
            handleRefreshReuse(hash);
            throw new BizException(ResultCode.REFRESH_INVALID);
        }
        if (session.getRefreshExpiresAt() == null
                || session.getRefreshExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.REFRESH_INVALID);
        }

        UserAccount account = userAccountMapper.findById(session.getUserId());
        if (account == null || account.getStatus() == null || account.getStatus() != 1) {
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        // 轮换：旧 refresh 立即作废，仅保留摘要用于泄露检测
        String oldAccessToken = null;
        if (session.getAccessJti() != null) {
            oldAccessToken = session.getAccessJti();
        }
        String newRefreshToken = jwtTokenProvider.createRefreshToken();
        session.setRefreshTokenHash(HashUtil.sha256Hex(newRefreshToken));
        session.setRefreshExpiresAt(LocalDateTime.ofInstant(jwtTokenProvider.refreshExpiresAt(), ZoneId.systemDefault()));
        String accessToken = jwtTokenProvider.createAccessToken(
                account.getId(), account.getUserNo(), session.getDeviceId(), account.getTokenVersion());
        session.setAccessJti(jwtTokenProvider.parse(accessToken).jti());
        session.setDeviceId(req.getDeviceId());
        deviceSessionMapper.upsert(session);

        // 旧 refresh 标记已使用（TTL 与 refresh 有效期一致）
        redis.opsForValue().set(REFRESH_REUSED_PREFIX + hash, "1", Duration.ofDays(30));
        if (oldAccessToken != null) {
            JwtClaims oldClaims;
            try {
                oldClaims = jwtTokenProvider.parse(oldAccessToken);
            } catch (BizException e) {
                oldClaims = null;
            }
            if (oldClaims != null) {
                tokenBlacklistService.blacklist(oldClaims.jti(), oldClaims.expiresAt());
            }
        }
        return buildTokenVO(account, accessToken, newRefreshToken);
    }

    @Override
    public void logout(String authorization) {
        UserContext context = AuthContext.get();
        if (context == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim() : authorization;
        if (token != null && !token.isBlank()) {
            try {
                JwtClaims claims = jwtTokenProvider.parse(token);
                tokenBlacklistService.blacklist(claims.jti(), claims.expiresAt());
            } catch (BizException e) {
                log.debug("登出时 token 已失效，忽略：{}", e.getMessage());
            }
        }
        deviceSessionMapper.invalidate(context.userId(), context.deviceId());
        presenceService.offline(context.userId(), context.deviceId());
    }

    @Override
    public UserVO me() {
        long userId = AuthContext.userId();
        UserAccount account = userAccountMapper.findById(userId);
        if (account == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userViewFactory.toVO(account, userProfileMapper.findByUserId(userId));
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordReq req) {
        long userId = AuthContext.userId();
        UserAccount account = userAccountMapper.findById(userId);
        if (account == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), account.getPasswordHash())) {
            throw new BizException(ResultCode.BAD_CREDENTIALS, "原密码不正确");
        }
        userAccountMapper.updatePasswordHash(userId, passwordEncoder.encode(req.getNewPassword()));
        userAccountMapper.bumpTokenVersion(userId);
        Integer newVersion = userAccountMapper.selectTokenVersion(userId);
        tokenVersionService.bump(userId, newVersion == null ? 1 : newVersion);
        deviceSessionMapper.invalidateAll(userId);
    }

    /**
     * refresh token 被重复使用：吊销该用户所有设备并递增令牌版本。
     */
    private void handleRefreshReuse(String hash) {
        Boolean reused = redis.hasKey(REFRESH_REUSED_PREFIX + hash);
        if (!Boolean.TRUE.equals(reused)) {
            return;
        }
        Long userId = null;
        UserDeviceSession any = deviceSessionMapper.findByRefreshHash(hash);
        if (any != null) {
            userId = any.getUserId();
        }
        if (userId == null) {
            return;
        }
        log.warn("检测到 refresh token 重复使用，吊销用户全部会话 userId={}", userId);
        deviceSessionMapper.invalidateAll(userId);
        userAccountMapper.bumpTokenVersion(userId);
        Integer version = userAccountMapper.selectTokenVersion(userId);
        tokenVersionService.bump(userId, version == null ? 1 : version);
    }

    private TokenVO buildTokenVO(UserAccount account, String accessToken, String refreshToken) {
        TokenVO vo = new TokenVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setExpiresIn(jwtTokenProvider.accessTtlSeconds());
        vo.setRefreshExpiresIn(jwtTokenProvider.refreshExpiresAt().getEpochSecond()
                - java.time.Instant.now().getEpochSecond());
        vo.setUser(userViewFactory.toVO(account, userProfileMapper.findByUserId(account.getId())));
        return vo;
    }
}
