package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.SessionSeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionSeatMapper {

    /** 查某场次的座位状态（JOIN hx_seats 获取行列号和类型） */
    List<java.util.Map<String, Object>> findSessionSeats(@Param("sessionId") Long sessionId);

    @Insert("INSERT IGNORE INTO hx_session_seats (session_id, seat_id, status, gmt_modify) " +
            "SELECT #{sessionId}, id, 'available', NOW() FROM hx_seats WHERE hall_id = #{hallId}")
    int initSeatsForSession(@Param("sessionId") Long sessionId, @Param("hallId") Long hallId);

    @Select("SELECT * FROM hx_session_seats WHERE session_id = #{sessionId} AND seat_id = #{seatId} ORDER BY status DESC LIMIT 1")
    SessionSeat findBySessionAndSeat(@Param("sessionId") Long sessionId, @Param("seatId") Long seatId);

    @Update("UPDATE hx_session_seats SET status = #{status}, locked_by_order_id = #{orderId}, locked_at = #{lockedAt} WHERE session_id = #{sessionId} AND seat_id = #{seatId}")
    int updateSeatStatus(@Param("sessionId") Long sessionId, @Param("seatId") Long seatId, @Param("status") String status, @Param("orderId") Long orderId, @Param("lockedAt") LocalDateTime lockedAt);

    @Update("UPDATE hx_session_seats SET status = #{status} WHERE locked_by_order_id = #{orderId}")
    int updateStatusByOrderId(@Param("orderId") Long orderId, @Param("status") String status);

    @Update("UPDATE hx_session_seats SET status = 'available', locked_by_order_id = NULL, locked_at = NULL WHERE locked_by_order_id = #{orderId}")
    int releaseSeatsByOrderId(@Param("orderId") Long orderId);

    /**
     * 按 session_id + seat_ids 精确释放座位（不依赖 locked_by_order_id，避免主键回填失败导致座位无法释放）
     * 动态 SQL 在 SessionSeatMapper.xml 中定义
     */
    int releaseSeatsBySessionAndSeatIds(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds);

    /**
     * 按 session_id + seat_ids 精确更新座位状态（用于支付成功标记 sold）
     * 动态 SQL 在 SessionSeatMapper.xml 中定义
     */
    int markSeatsStatusBySessionAndSeatIds(@Param("sessionId") Long sessionId, @Param("seatIds") List<Long> seatIds, @Param("status") String status);

    @Select("SELECT COUNT(*) FROM hx_session_seats WHERE session_id = #{sessionId} AND status = #{status}")
    int countByStatus(@Param("sessionId") Long sessionId, @Param("status") String status);

    /** 删除孤儿座位状态 — 直接删除不在场次表中的记录 */
    @org.apache.ibatis.annotations.Delete("DELETE FROM hx_session_seats WHERE session_id NOT IN (SELECT id FROM hx_sessions)")
    int deleteAllOrphanSeatStatuses();

    /** 查最大的 session_id */
    @Select("SELECT MAX(session_id) FROM hx_session_seats")
    Long findMaxSessionId();
}