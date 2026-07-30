package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Seat;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeatMapper {

    @Select("SELECT * FROM hx_seats WHERE hall_id = #{hallId} ORDER BY row_num, col_num")
    List<Seat> findByHallId(@Param("hallId") Long hallId);

    @Select("SELECT * FROM hx_seats WHERE id = #{id}")
    Seat findById(@Param("id") Long id);

    int insert(Seat seat);
    int deleteByHallId(@Param("hallId") Long hallId);

    /**
     * 批量初始化一个影厅的所有座位（一条SQL搞定）
     */
    @Insert("INSERT IGNORE INTO hx_seats (hall_id, row_num, col_num, seat_type, gmt_create) " +
            "SELECT #{hallId}, r.n, c.n, " +
            "CASE WHEN r.n <= 2 THEN 'vip' WHEN r.n >= #{totalRows} - 1 THEN 'couple' ELSE 'normal' END, NOW() " +
            "FROM (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 " +
            "UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 " +
            "UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15) r " +
            "CROSS JOIN (SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 " +
            "UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10 " +
            "UNION ALL SELECT 11 UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15) c " +
            "WHERE r.n <= #{totalRows} AND c.n <= #{totalCols}")
    int initSeatsForHall(@Param("hallId") Long hallId, @Param("totalRows") int totalRows, @Param("totalCols") int totalCols);
}