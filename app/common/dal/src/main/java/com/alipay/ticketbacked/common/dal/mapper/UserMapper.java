package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper {

    @Select("SELECT * FROM hx_users WHERE username = #{username}")
    User findByUsername(@Param("username") String username);

    @Select("SELECT * FROM hx_users WHERE id = #{id}")
    User findById(@Param("id") Long id);

    int insert(User user);
    int updateIsActive(@Param("id") Long id, @Param("isActive") Boolean isActive);

    /** Admin: 用户列表 */
    List<User> findAllForAdmin(@Param("isActive") Boolean isActive, @Param("search") String search, @Param("limit") int limit, @Param("offset") int offset);

    /** Admin: 用户总数（按条件过滤） */
    int countAllForAdmin(@Param("isActive") Boolean isActive, @Param("search") String search);

    /**
     * Admin: 用户总数（无过滤，替代 findAllForAdmin(null,null,999999,0).size() 全表拉内存再计数）
     */
    @Select("SELECT COUNT(*) FROM hx_users")
    int countAll();
}