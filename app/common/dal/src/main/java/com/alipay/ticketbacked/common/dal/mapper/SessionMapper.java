package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Session;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionMapper {

    @Select("SELECT * FROM hx_sessions WHERE status != 'closed' ORDER BY start_time")
    List<Session> findAllAvailable();

    @Select("SELECT * FROM hx_sessions WHERE movie_id = #{movieId} AND status != 'closed' ORDER BY start_time")
    List<Session> findByMovieId(@Param("movieId") Long movieId);

    @Select("SELECT * FROM hx_sessions WHERE cinema_id = #{cinemaId} AND status != 'closed' ORDER BY start_time")
    List<Session> findByCinemaId(@Param("cinemaId") Long cinemaId);

    @Select("SELECT * FROM hx_sessions WHERE movie_id = #{movieId} AND cinema_id = #{cinemaId} AND status != 'closed' ORDER BY start_time")
    List<Session> findByMovieAndCinema(@Param("movieId") Long movieId, @Param("cinemaId") Long cinemaId);

    List<Session> findByMovieIdAndDate(@Param("movieId") Long movieId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    List<Session> findByCinemaIdAndDate(@Param("cinemaId") Long cinemaId, @Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Select("SELECT * FROM hx_sessions WHERE id = #{id}")
    Session findById(@Param("id") Long id);

    List<Session> findByIds(@Param("ids") List<Long> ids);

    int insert(Session session);
    int update(Session session);
    int deleteById(@Param("id") Long id);

    /** 获取有场次的电影ID */
    @Select("SELECT DISTINCT movie_id FROM hx_sessions")
    List<Long> findMovieIdsWithSessions();
}