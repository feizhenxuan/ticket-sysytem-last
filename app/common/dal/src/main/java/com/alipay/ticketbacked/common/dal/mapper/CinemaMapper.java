package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Cinema;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CinemaMapper {

    @Select("SELECT * FROM hx_cinemas ORDER BY id LIMIT #{limit}")
    List<Cinema> findAll(@Param("limit") int limit);

    @Select("SELECT * FROM hx_cinemas")
    List<Cinema> findAllNoLimit();

    @Select("SELECT * FROM hx_cinemas WHERE city = #{city} ORDER BY id LIMIT #{limit}")
    List<Cinema> findByCity(@Param("city") String city, @Param("limit") int limit);

    @Select("SELECT * FROM hx_cinemas WHERE city LIKE CONCAT('%', #{keyword}, '%') ORDER BY id LIMIT #{limit}")
    List<Cinema> findByCityKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    @Select("SELECT * FROM hx_cinemas WHERE id = #{id}")
    Cinema findById(@Param("id") Long id);

    @Select("SELECT * FROM hx_cinemas WHERE name LIKE CONCAT('%', #{name}, '%') LIMIT #{limit}")
    List<Cinema> searchByName(@Param("name") String name, @Param("limit") int limit);

    int insert(Cinema cinema);
    int update(Cinema cinema);
    int deleteById(@Param("id") Long id);
}