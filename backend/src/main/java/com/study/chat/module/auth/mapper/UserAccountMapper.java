package com.study.chat.module.auth.mapper;

import com.study.chat.module.auth.entity.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

public interface UserAccountMapper {

    @Select("select * from user_account where account = #{account} and deleted_at is null limit 1")
    UserAccount findByAccount(@Param("account") String account);

    @Select("select * from user_account where id = #{userId} and deleted_at is null limit 1")
    UserAccount findById(@Param("userId") Long userId);

    @Select("select * from user_account where user_no = #{userNo} and deleted_at is null limit 1")
    UserAccount findByUserNo(@Param("userNo") String userNo);

    @Select("select ifnull(token_version, 1) from user_account where id = #{userId}")
    Integer selectTokenVersion(@Param("userId") Long userId);

    /**
     * 账号 / 用户号模糊搜索：只返回账户字段，资料由 profile 补齐。
     */
    @Select("select * from user_account where deleted_at is null and status = 1 "
            + "and (account like concat('%', #{keyword}, '%') or user_no = #{keyword}) "
            + "limit #{size}")
    List<UserAccount> search(@Param("keyword") String keyword, @Param("size") int size);

    @Insert("insert into user_account (user_no, account, phone, email, password_hash, password_algo, status, token_version) "
            + "values (#{userNo}, #{account}, #{phone}, #{email}, #{passwordHash}, #{passwordAlgo}, #{status}, #{tokenVersion})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount account);

    @Update("update user_account set last_login_at = #{loginAt}, last_login_ip = #{ip} where id = #{userId}")
    int updateLastLogin(@Param("userId") Long userId,
                        @Param("loginAt") LocalDateTime loginAt,
                        @Param("ip") String ip);

    /**
     * 递增令牌版本并返回新值：改密码 / 一键下线所有设备时使用。
     */
    @Update("update user_account set token_version = token_version + 1 where id = #{userId}")
    int bumpTokenVersion(@Param("userId") Long userId);

    @Update("update user_account set password_hash = #{passwordHash} where id = #{userId}")
    int updatePasswordHash(@Param("userId") Long userId, @Param("passwordHash") String passwordHash);
}
