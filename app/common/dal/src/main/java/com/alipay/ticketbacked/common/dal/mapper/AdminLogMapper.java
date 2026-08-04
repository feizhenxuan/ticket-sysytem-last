package com.alipay.ticketbacked.common.dal.mapper;
import com.alipay.ticketbacked.core.model.AdminLog;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper
public interface AdminLogMapper {
    int insert(AdminLog log);
    @Select("SELECT * FROM hx_admin_logs ORDER BY gmt_create DESC LIMIT #{limit} OFFSET #{offset}")
    List<AdminLog> findAll(@Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM hx_admin_logs")
    int countAll();
    @Select("SELECT * FROM hx_admin_logs WHERE module = #{module} ORDER BY gmt_create DESC LIMIT #{limit} OFFSET #{offset}")
    List<AdminLog> findByModule(@Param("module") String module, @Param("limit") int limit, @Param("offset") int offset);
    @Select("SELECT COUNT(*) FROM hx_admin_logs WHERE module = #{module}")
    int countByModule(@Param("module") String module);
}
