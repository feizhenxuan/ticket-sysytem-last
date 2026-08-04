package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Session;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionMapper {

    @Select("SELECT * FROM hx_sessions ORDER BY start_time")
    List<Session> findAll();

    @Select("SELECT * FROM hx_sessions WHERE status != 'closed' ORDER BY start_time")
    List<Session> findAllAvailable();

    /** 查所有当天及以后的可用场次，按票价升序排序 */
    @Select("SELECT * FROM hx_sessions WHERE status != 'closed' AND start_time >= #{startTime} ORDER BY price ASC LIMIT #{limit}")
    List<Session> findAllAvailableOrderByPrice(@Param("startTime") LocalDateTime startTime, @Param("limit") int limit);

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

    /** 批量删除指定日期之前的场次 */
    @Delete("DELETE FROM hx_sessions WHERE start_time < #{beforeDate}")
    int deleteBeforeDate(@Param("beforeDate") LocalDateTime beforeDate);

    /** 删除指定某一天的场次 */
    @Delete("DELETE FROM hx_sessions WHERE start_time >= #{startDate} AND start_time < #{endDate}")
    int deleteByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /** 删除所有场次 */
    @Delete("DELETE FROM hx_sessions")
    int deleteAll();

    /**
     * Admin: 场次总数与可售场次一条 SQL 搞定（替代 findAllAvailable() 拉全表再内存 filter）
     * 返回: { total, available }（均已排除 status='closed'）
     */
    @Select("SELECT COUNT(*) as total, " +
            "SUM(CASE WHEN status = 'available' THEN 1 ELSE 0 END) as available " +
            "FROM hx_sessions WHERE status != 'closed'")
    java.util.Map<String, Object> countSummary();
}