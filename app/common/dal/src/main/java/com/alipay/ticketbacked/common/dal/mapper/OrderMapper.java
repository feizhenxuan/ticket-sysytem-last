package com.alipay.ticketbacked.common.dal.mapper;

import com.alipay.ticketbacked.core.model.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper {

    @Select("SELECT * FROM hx_orders WHERE id = #{id} AND user_id = #{userId}")
    Order findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    @Select("SELECT * FROM hx_orders WHERE order_no = #{orderNo}")
    Order findByOrderNo(@Param("orderNo") String orderNo);

    @Select("SELECT * FROM hx_orders WHERE user_id = #{userId} ORDER BY gmt_create DESC")
    List<Order> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM hx_orders WHERE user_id = #{userId} AND status = #{status} ORDER BY gmt_create DESC")
    List<Order> findByUserIdAndStatus(@Param("userId") Long userId, @Param("status") String status);

    @Select("SELECT * FROM hx_orders WHERE status = 'pending' AND gmt_create < #{expiryTime}")
    List<Order> findExpiredPendingOrders(@Param("expiryTime") LocalDateTime expiryTime);

    int insert(Order order);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("tradeNo") String tradeNo, @Param("pickupCode") String pickupCode, @Param("paidAt") LocalDateTime paidAt);

    int updateCancelStatus(@Param("id") Long id, @Param("status") String status, @Param("cancelledAt") LocalDateTime cancelledAt);

    int updateRefundStatus(@Param("id") Long id, @Param("status") String status, @Param("refundedAt") LocalDateTime refundedAt);

    /** Admin: 全部订单 */
    List<Order> findAllForAdmin(@Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    /** Admin: 按 ID 查订单（不限用户） */
    @Select("SELECT * FROM hx_orders WHERE id = #{id}")
    Order findById(@Param("id") Long id);

    /** Admin: 统计各状态订单数 */
    @Select("SELECT status, COUNT(*) as cnt FROM hx_orders GROUP BY status")
    List<java.util.Map<String, Object>> countByStatus();

    /**
     * Admin: 按状态聚合订单数与已支付收入（替代全表拉内存再 count/sum）
     * 返回每状态一行: { status, cnt, revenue }
     * revenue 仅对 status='paid' 有意义，其他状态为 0
     */
    @Select("SELECT status, COUNT(*) as cnt, " +
            "COALESCE(SUM(CASE WHEN status = 'paid' THEN total_amount ELSE 0 END), 0) as revenue " +
            "FROM hx_orders GROUP BY status")
    List<java.util.Map<String, Object>> statsSummary();

    /** Admin: 最近 N 天每日订单数和金额 */
    List<java.util.Map<String, Object>> dailyRevenueTrend(@Param("days") int days);

    /** Admin: 电影票房排行（按已支付订单的 ticket_count * price 汇总） */
    List<java.util.Map<String, Object>> movieRanking(@Param("limit") int limit);

    /** Admin: 订单列表（带关联数据：用户名、电影名、影院名、场次时间、影厅名） */
    List<java.util.Map<String, Object>> findAllForAdminWithDetails(@Param("status") String status, @Param("limit") int limit, @Param("offset") int offset);

    /** Admin: 订单总数（按状态过滤） */
    int countAllForAdmin(@Param("status") String status);

    /** Admin: 订单详情（带关联数据） */
    java.util.Map<String, Object> findByIdWithDetails(@Param("id") Long id);

    /** Admin: 按用户ID统计订单数 */
    @Select("SELECT COUNT(*) FROM hx_orders WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    /** Admin: 按场次ID统计订单数 */
    @Select("SELECT COUNT(*) FROM hx_orders WHERE session_id = #{sessionId}")
    int countBySessionId(@Param("sessionId") Long sessionId);
}