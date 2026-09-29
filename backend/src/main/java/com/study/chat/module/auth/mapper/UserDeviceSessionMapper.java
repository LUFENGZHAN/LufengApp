package com.study.chat.module.auth.mapper;

import com.study.chat.module.auth.entity.UserDeviceSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserDeviceSessionMapper {

    @Select("select * from user_device_session where user_id = #{userId} and device_id = #{deviceId} limit 1")
    UserDeviceSession findByUserAndDevice(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Select("select * from user_device_session where refresh_token_hash = #{hash} limit 1")
    UserDeviceSession findByRefreshHash(@Param("hash") String hash);

    /**
     * 同一用户 + 同一设备重复登录时复用一行，等价于「该设备旧会话下线」。
     */
    @Insert("""
            insert into user_device_session
                (user_id, device_id, device_type, device_name, refresh_token_hash,
                 refresh_expires_at, access_jti, client_ip, status, login_at, last_active_at)
            values
                (#{userId}, #{deviceId}, #{deviceType}, #{deviceName}, #{refreshTokenHash},
                 #{refreshExpiresAt}, #{accessJti}, #{clientIp}, 1, now(3), now(3))
            on duplicate key update
                refresh_token_hash = values(refresh_token_hash),
                refresh_expires_at = values(refresh_expires_at),
                access_jti       = values(access_jti),
                device_type      = values(device_type),
                client_ip        = values(client_ip),
                status           = 1,
                login_at         = now(3),
                last_active_at   = now(3)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int upsert(UserDeviceSession session);

    @Update("update user_device_session set status = 0 where user_id = #{userId} and device_id = #{deviceId}")
    int invalidate(@Param("userId") Long userId, @Param("deviceId") String deviceId);

    @Update("update user_device_session set status = 0 where user_id = #{userId}")
    int invalidateAll(@Param("userId") Long userId);

    @Update("update user_device_session set access_jti = #{jti}, last_active_at = now(3) where user_id = #{userId} and device_id = #{deviceId}")
    int updateAccessJti(@Param("userId") Long userId,
                        @Param("deviceId") String deviceId,
                        @Param("jti") String jti);

    @Update("update user_device_session set last_active_at = now(3) where id = #{id}")
    int touch(@Param("id") Long id);
}
