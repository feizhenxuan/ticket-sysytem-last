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

    /**
     * Admin: 影厅总数（替代「遍历每个影院再 findByCinemaId().size()」的 N+1 查询）
     */
    @Select("SELECT COUNT(*) FROM hx_halls")
    int countAll();

    List<Hall> findByIds(@Param("ids") List<Long> ids);

    int insert(Hall hall);
    int update(Hall hall);
    int deleteById(@Param("id") Long id);
}