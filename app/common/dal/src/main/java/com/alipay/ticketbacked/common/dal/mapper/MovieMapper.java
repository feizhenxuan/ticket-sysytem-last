package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Movie;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface MovieMapper {

    @Select("SELECT * FROM hx_movies WHERE status = #{status} ORDER BY rating DESC LIMIT #{limit}")
    List<Movie> findByStatusOrderByRating(@Param("status") String status, @Param("limit") int limit);

    @Select("SELECT * FROM hx_movies ORDER BY rating DESC LIMIT #{limit}")
    List<Movie> findAllOrderByRating(@Param("limit") int limit);

    @Select("SELECT * FROM hx_movies WHERE status = #{status} ORDER BY release_date DESC LIMIT #{limit}")
    List<Movie> findByStatusOrderByRelease(@Param("status") String status, @Param("limit") int limit);

    /** 按最低票价排序（JOIN hx_sessions） */
    List<Movie> findOrderByMinPrice(@Param("status") String status, @Param("limit") int limit);

    @Select("SELECT * FROM hx_movies WHERE id = #{id}")
    Movie findById(@Param("id") Long id);

    /** 模糊搜索 */
    @Select("SELECT * FROM hx_movies WHERE title LIKE CONCAT('%', #{keyword}, '%') AND status = #{status} LIMIT #{limit}")
    List<Movie> searchByKeyword(@Param("keyword") String keyword, @Param("status") String status, @Param("limit") int limit);

    /** 按类型筛选 */
    @Select("SELECT * FROM hx_movies WHERE genre LIKE CONCAT('%', #{genre}, '%') AND status = #{status} ORDER BY rating DESC LIMIT #{limit}")
    List<Movie> findByGenre(@Param("genre") String genre, @Param("status") String status, @Param("limit") int limit);

    int insert(Movie movie);
    int update(Movie movie);
    int deleteById(@Param("id") Long id);

    /** 所有有场次的电影ID（用于过滤） */
    @Select("SELECT DISTINCT movie_id FROM hx_sessions")
    List<Long> findMovieIdsWithSessions();

    /**
     * Admin: 按状态聚合电影数（替代 findByStatusOrderByRating(...).size() 全表拉内存再计数）
     * 返回每状态一行: { status, cnt }
     */
    @Select("SELECT status, COUNT(*) as cnt FROM hx_movies GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus();
}