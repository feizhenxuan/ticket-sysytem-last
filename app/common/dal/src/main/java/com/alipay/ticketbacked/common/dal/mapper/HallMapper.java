package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Hall;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HallMapper {

    @Select("SELECT * FROM hx_halls WHERE cinema_id = #{cinemaId}")
    List<Hall> findByCinemaId(@Param("cinemaId") Long cinemaId);

    @Select("SELECT * FROM hx_halls WHERE id = #{id}")
    Hall findById(@Param("id") Long id);

    List<Hall> findByIds(@Param("ids") List<Long> ids);

    int insert(Hall hall);
    int update(Hall hall);
    int deleteById(@Param("id") Long id);
}