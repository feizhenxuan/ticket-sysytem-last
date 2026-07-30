package com.alipay.ticketbacked.common.dal;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * 安全的 LocalDateTime 类型处理器
 *
 * MyBatis 默认的 LocalDateTimeTypeHandler 调用 rs.getObject(columnName, LocalDateTime.class)，
 * 而 MySQL JDBC 驱动 (JDBC42ResultSet) 在该方法中对 NULL 值未做空检查，
 * 直接调用 getTimestamp().toLocalDateTime() 导致空指针异常。
 *
 * 本 TypeHandler 改用 rs.getTimestamp() 并手动做空值判断，规避驱动层 Bug。
 */
@MappedTypes(LocalDateTime.class)
public class SafeLocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setTimestamp(i, Timestamp.valueOf(parameter));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnName);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        Timestamp timestamp = cs.getTimestamp(columnIndex);
        return timestamp != null ? timestamp.toLocalDateTime() : null;
    }
}
