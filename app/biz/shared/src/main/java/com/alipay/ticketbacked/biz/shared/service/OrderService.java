package com.alipay.ticketbacked.biz.shared.service;

import com.alipay.ticketbacked.common.dal.mapper.*;
import com.alipay.ticketbacked.core.model.*;
import com.alipay.ticketbacked.core.model.BizException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务 — 对应 Python api/orders.py
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderMapper orderMapper;
    private final SessionMapper sessionMapper;
    private final SessionSeatMapper sessionSeatMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final HallMapper hallMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderService(OrderMapper orderMapper, SessionMapper sessionMapper,
                        SessionSeatMapper sessionSeatMapper, MovieMapper movieMapper,
                        CinemaMapper cinemaMapper, HallMapper hallMapper) {
        this.orderMapper = orderMapper;
        this.sessionMapper = sessionMapper;
        this.sessionSeatMapper = sessionSeatMapper;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.hallMapper = hallMapper;
    }

    /** 创建订单（锁定座位） */
    @Transactional
    public Order createOrder(Long userId, Long sessionId, List<Long> seatIds) {
        log.info("[createOrder] start: userId={}, sessionId={}, seatIds={}", userId, sessionId, seatIds);
        Session session = sessionMapper.findById(sessionId);
        log.info("[createOrder] session={}", session);
        if (session == null) throw BizException.notFound("场次不存在");
        log.info("[createOrder] session.getPrice()={}", session.getPrice());

        // 检查座位是否可选
        for (Long seatId : seatIds) {
            SessionSeat ss = sessionSeatMapper.findBySessionAndSeat(sessionId, seatId);
            log.info("[createOrder] seat {} status: {}", seatId, ss);
            if (ss == null || !"available".equals(ss.getStatus())) {
                throw BizException.badRequest("座位 " + seatId + " 不可选");
            }
        }

        // 创建订单
        Order order = new Order();
        order.setOrderNo("TK" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + (int)(Math.random() * 9000 + 1000));
        order.setUserId(userId);
        order.setSessionId(sessionId);
        try {
            order.setSeatIds(objectMapper.writeValueAsString(seatIds));
        } catch (Exception e) {
            order.setSeatIds("[]");
        }
        order.setTicketCount(seatIds.size());
        order.setTotalAmount(session.getPrice().multiply(BigDecimal.valueOf(seatIds.size())));
        order.setStatus("pending");
        log.info("[createOrder] before insert: order={}", order);
        orderMapper.insert(order);
        log.info("[createOrder] after insert: order.getId()={}", order.getId());

        // 锁定座位
        for (Long seatId : seatIds) {
            sessionSeatMapper.updateSeatStatus(sessionId, seatId, "locked", order.getId(), LocalDateTime.now());
        }
        return order;
    }

    /** 查看我的订单 */
    public List<Map<String, Object>> listOrders(Long userId, String statusFilter) {
        List<Order> orders = statusFilter != null && !statusFilter.isBlank()
                ? orderMapper.findByUserIdAndStatus(userId, statusFilter)
                : orderMapper.findByUserId(userId);

        if (orders.isEmpty()) return Collections.emptyList();

        // 预加载关联
        Set<Long> sessionIds = orders.stream().map(Order::getSessionId).collect(Collectors.toSet());
        List<Session> sessions = sessionMapper.findByIds(new ArrayList<>(sessionIds));
        Set<Long> movieIds = sessions.stream().map(Session::getMovieId).collect(Collectors.toSet());
        Set<Long> cinemaIds = sessions.stream().map(Session::getCinemaId).collect(Collectors.toSet());
        Set<Long> hallIds = sessions.stream().map(Session::getHallId).collect(Collectors.toSet());

        Map<Long, Movie> movieMap = new HashMap<>();
        for (Long mid : movieIds) { Movie m = movieMapper.findById(mid); if (m != null) movieMap.put(mid, m); }
        Map<Long, Cinema> cinemaMap = new HashMap<>();
        for (Long cid : cinemaIds) { Cinema c = cinemaMapper.findById(cid); if (c != null) cinemaMap.put(cid, c); }
        Map<Long, Hall> hallMap = hallMapper.findByIds(new ArrayList<>(hallIds)).stream()
                .collect(Collectors.toMap(Hall::getId, h -> h));

        Map<Long, Map<String, Object>> sessionMap = new HashMap<>();
        for (Session s : sessions) {
            Map<String, Object> info = new HashMap<>();
            info.put("start_time", s.getStartTime());
            info.put("price", s.getPrice());
            Movie m = movieMap.get(s.getMovieId());
            info.put("movie_title", m != null ? m.getTitle() : "");
            info.put("poster_url", m != null ? m.getPosterUrl() : null);
            Cinema c = cinemaMap.get(s.getCinemaId());
            info.put("cinema_name", c != null ? c.getName() : "");
            Hall h = hallMap.get(s.getHallId());
            info.put("hall_name", h != null ? h.getName() : "");
            sessionMap.put(s.getId(), info);
        }

        return orders.stream().map(o -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", o.getId());
            item.put("order_no", o.getOrderNo());
            item.put("session_id", o.getSessionId());
            item.put("seat_ids", o.getSeatIds());
            item.put("ticket_count", o.getTicketCount());
            item.put("total_amount", o.getTotalAmount());
            item.put("status", o.getStatus());
            item.put("created_at", o.getGmtCreate());
            item.putAll(sessionMap.getOrDefault(o.getSessionId(), Collections.emptyMap()));
            return item;
        }).collect(Collectors.toList());
    }

    public Order getOrder(Long orderId, Long userId) {
        return orderMapper.findByIdAndUser(orderId, userId);
    }

    /** 取消订单（释放座位） */
    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        Order order = orderMapper.findByIdAndUser(orderId, userId);
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"pending".equals(order.getStatus())) throw BizException.badRequest("只能取消待支付订单");

        orderMapper.updateCancelStatus(orderId, "cancelled", LocalDateTime.now());
        sessionSeatMapper.releaseSeatsByOrderId(orderId);
    }

    /** 确认支付 */
    @Transactional
    public Map<String, Object> confirmPayment(String orderNo, String tradeNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) return Map.of("success", false, "message", "订单不存在");
        if (!"pending".equals(order.getStatus())) {
            return Map.of("success", true, "message", "订单已处理", "status", order.getStatus());
        }

        String pickupCode = String.valueOf((int)(Math.random() * 900000 + 100000));
        orderMapper.updateStatus(order.getId(), "paid", tradeNo, pickupCode, LocalDateTime.now());
        sessionSeatMapper.updateStatusByOrderId(order.getId(), "sold");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "支付成功");
        result.put("order_no", orderNo);
        result.put("pickup_code", pickupCode);
        result.put("status", "paid");
        return result;
    }

    /** 退票退款 */
    @Transactional
    public void refundOrder(Long orderId, Long userId) {
        Order order = orderMapper.findByIdAndUser(orderId, userId);
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"paid".equals(order.getStatus())) throw BizException.badRequest("只能退已支付订单");

        orderMapper.updateRefundStatus(orderId, "refunded", LocalDateTime.now());
        sessionSeatMapper.releaseSeatsByOrderId(orderId);
    }

    /** 获取用户已支付订单（供 Agent Function Calling 使用） */
    public List<Map<String, Object>> getUserOrders(Long userId, String statusFilter) {
        return listOrders(userId, statusFilter);
    }
}