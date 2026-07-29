package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Seat;
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
}