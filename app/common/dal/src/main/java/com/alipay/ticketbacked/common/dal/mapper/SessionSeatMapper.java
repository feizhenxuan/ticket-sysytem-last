package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.SessionSeat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SessionSeatMapper {

    /** 查某场次的座位状态（JOIN hx_seats 获取行列号和类型） */
    List<java.util.Map<String, Object>> findSessionSeats(@Param("sessionId") Long sessionId);

    @Select("SELECT * FROM hx_session_seats WHERE session_id = #{sessionId} AND seat_id = #{seatId} LIMIT 1")
    SessionSeat findBySessionAndSeat(@Param("sessionId") Long sessionId, @Param("seatId") Long seatId);

    @Update("UPDATE hx_session_seats SET status = #{status}, locked_by_order_id = #{orderId}, locked_at = #{lockedAt} WHERE session_id = #{sessionId} AND seat_id = #{seatId} LIMIT 1")
    int updateSeatStatus(@Param("sessionId") Long sessionId, @Param("seatId") Long seatId, @Param("status") String status, @Param("orderId") Long orderId, @Param("lockedAt") LocalDateTime lockedAt);

    @Update("UPDATE hx_session_seats SET status = #{status} WHERE locked_by_order_id = #{orderId}")
    int updateStatusByOrderId(@Param("orderId") Long orderId, @Param("status") String status);

    @Update("UPDATE hx_session_seats SET status = 'available', locked_by_order_id = NULL, locked_at = NULL WHERE locked_by_order_id = #{orderId}")
    int releaseSeatsByOrderId(@Param("orderId") Long orderId);

    @Select("SELECT COUNT(*) FROM hx_session_seats WHERE session_id = #{sessionId} AND status = #{status}")
    int countByStatus(@Param("sessionId") Long sessionId, @Param("status") String status);
}