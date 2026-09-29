package com.study.chat.module.auth.mapper;

import com.study.chat.module.auth.entity.UserProfile;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface UserProfileMapper {

    @Select("select * from user_profile where user_id = #{userId} limit 1")
    UserProfile findByUserId(@Param("userId") Long userId);

    @Insert("insert into user_profile (user_id, nickname, avatar, gender) "
            + "values (#{userId}, #{nickname}, #{avatar}, #{gender})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserProfile profile);

    @Update("""
            <script>
            update user_profile
            <set>
                <if test="nickname != null">nickname = #{nickname},</if>
                <if test="avatar != null">avatar = #{avatar},</if>
                <if test="gender != null">gender = #{gender},</if>
                <if test="signature != null">signature = #{signature},</if>
                <if test="region != null">region = #{region},</if>
                <if test="birthday != null">birthday = #{birthday},</if>
            </set>
            where user_id = #{userId}
            </script>
            """)
    int updateSelective(UserProfile profile);
}
