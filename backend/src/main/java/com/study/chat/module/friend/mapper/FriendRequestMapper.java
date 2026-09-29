package com.study.chat.module.friend.mapper;

import com.study.chat.module.friend.entity.FriendRequest;
import com.study.chat.module.friend.entity.FriendRequestRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FriendRequestMapper {

    /**
     * 注意：apply_msg 是 NOT NULL 列，DDL 上的 DEFAULT '' 对「显式插入 NULL」无效，
     * 因此这里必须用 ifnull 兜底，避免调用方未填留言时报 1048。
     */
    @Insert("insert into friend_request (request_no, from_user_id, to_user_id, apply_msg, status) "
            + "values (#{requestNo}, #{fromUserId}, #{toUserId}, ifnull(#{applyMsg}, ''), 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FriendRequest request);

    @Select("select * from friend_request where id = #{id} limit 1")
    FriendRequest findById(@Param("id") Long id);

    /**
     * 未处理的申请（任一方向），用于申请去重。
     */
    @Select("select * from friend_request where from_user_id = #{from} and to_user_id = #{to} "
            + "and status = 0 limit 1")
    FriendRequest findPending(@Param("from") Long from, @Param("to") Long to);

    @Select("""
            select r.*, a.user_no, p.nickname, p.avatar
            from friend_request r
            join user_account a on a.id = r.from_user_id
            left join user_profile p on p.user_id = r.from_user_id
            where r.to_user_id = #{userId} and r.status = #{status}
            order by r.id desc
            """)
    List<FriendRequestRow> listIncoming(@Param("userId") Long userId, @Param("status") Integer status);

    @Select("""
            select r.*, a.user_no, p.nickname, p.avatar
            from friend_request r
            join user_account a on a.id = r.to_user_id
            left join user_profile p on p.user_id = r.to_user_id
            where r.from_user_id = #{userId} and r.status = #{status}
            order by r.id desc
            """)
    List<FriendRequestRow> listOutgoing(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * CAS 处理申请：只有仍处于「待处理」状态的更新才会生效，避免并发重复处理。
     */
    @Update("update friend_request set status = #{status}, handled_at = now(3) "
            + "where id = #{id} and status = 0")
    int handle(@Param("id") Long id, @Param("status") Integer status);

    /**
     * 删除好友后作废双方之间「已同意」的历史申请（置为 3 已忽略），
     * 避免申请列表里残留一条指向已解除关系的记录，同时不影响重新发起申请。
     */
    @Update("update friend_request set status = 3 "
            + "where status = 1 and ((from_user_id = #{a} and to_user_id = #{b}) "
            + "or (from_user_id = #{b} and to_user_id = #{a}))")
    int invalidateHandled(@Param("a") Long a, @Param("b") Long b);
}
