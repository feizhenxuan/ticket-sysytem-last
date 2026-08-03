package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Admin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminMapper {

    @Select("SELECT * FROM hx_admins WHERE username = #{username}")
    Admin findByUsername(@Param("username") String username);

    @Select("SELECT * FROM hx_admins WHERE id = #{id}")
    Admin findById(@Param("id") Long id);

    int insert(Admin admin);
}