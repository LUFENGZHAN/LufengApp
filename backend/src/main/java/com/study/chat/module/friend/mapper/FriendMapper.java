package com.study.chat.module.friend.mapper;

import com.study.chat.module.friend.entity.Friend;
import com.study.chat.module.friend.entity.FriendRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FriendMapper {

    @Select("select * from friend where user_id = #{userId} and friend_id = #{friendId} limit 1")
    Friend findOne(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Select("""
            select f.*, a.user_no friend_no, p.nickname, p.avatar, p.signature
            from friend f
            join user_account a on a.id = f.friend_id and a.deleted_at is null
            left join user_profile p on p.user_id = f.friend_id
            where f.user_id = #{userId}
            order by f.id desc
            """)
    List<FriendRow> listByUser(@Param("userId") Long userId);

    /**
     * 双向插入：同意好友申请时使用，唯一键保证重复操作不产生脏数据。
     */
    /**
     * remark 为 NOT NULL 列，source 可空；同样用 ifnull 兜底避免显式插入 NULL 报错。
     * 备注为空时不覆盖已有备注（保留旧值），仅首次写入时落空串。
     */
    @Insert("""
            insert into friend (user_id, friend_id, remark, source)
            values (#{userId}, #{friendId}, ifnull(#{remark}, ''), ifnull(#{source}, 'search'))
            on duplicate key update
                remark = if(#{remark} is null or #{remark} = '', remark, values(remark)),
                source = ifnull(values(source), source)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIgnoreDuplicate(Friend friend);

    /**
     * 精确更新备注：传入空串即表示清空（与 insertIgnoreDuplicate 的「空值不覆盖」语义区分开）。
     */
    @Update("update friend set remark = #{remark} where user_id = #{userId} and friend_id = #{friendId}")
    int updateRemark(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("remark") String remark);

    @Delete("delete from friend where (user_id = #{a} and friend_id = #{b}) or (user_id = #{b} and friend_id = #{a})")
    int deleteBoth(@Param("a") Long a, @Param("b") Long b);

    @Select("select count(1) from friend where user_id = #{userId} and friend_id = #{friendId}")
    int countRelation(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
