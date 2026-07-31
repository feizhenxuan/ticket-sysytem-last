package com.alipay.ticketbacked.biz.service.impl;

import com.alipay.ticketbacked.common.dal.mapper.OrderMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.core.model.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 定时任务 — 对应 Python core/scheduler.py
 * 每30秒扫描，5分钟未支付的订单自动取消并释放座位
 */
@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderMapper orderMapper;
    private final SessionSeatMapper sessionSeatMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderTimeoutScheduler(OrderMapper orderMapper, SessionSeatMapper sessionSeatMapper) {
        this.orderMapper = orderMapper;
        this.sessionSeatMapper = sessionSeatMapper;
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void cancelExpiredOrders() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(5);
        List<Order> expired = orderMapper.findExpiredPendingOrders(expiryTime);
        for (Order order : expired) {
            orderMapper.updateCancelStatus(order.getId(), "cancelled", LocalDateTime.now());
            releaseSeatsForOrder(order);
        }
        if (!expired.isEmpty()) {
            log.info("[Scheduler] 已取消 {} 个超时订单", expired.size());
        }
    }

    /**
     * 释放订单关联的座位 — 双重保险：
     * 1. 按 session_id + seat_ids 精确释放（不依赖 locked_by_order_id）
     * 2. 按 locked_by_order_id 兜底释放
     */
    @SuppressWarnings("unchecked")
    private void releaseSeatsForOrder(Order order) {
        // 精确释放
        List<Long> seatIds = parseSeatIds(order.getSeatIds());
        if (order.getSessionId() != null && !seatIds.isEmpty()) {
            int rows = sessionSeatMapper.releaseSeatsBySessionAndSeatIds(order.getSessionId(), seatIds);
            log.info("[Scheduler] 精确释放 {} 个座位, affected={}, orderId={}", seatIds.size(), rows, order.getId());
        }
        // 兜底释放
        if (order.getId() != null) {
            sessionSeatMapper.releaseSeatsByOrderId(order.getId());
        }
    }

    private List<Long> parseSeatIds(String seatIdsStr) {
        if (seatIdsStr == null || seatIdsStr.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(seatIdsStr, List.class);
        } catch (Exception e) {
            log.warn("[Scheduler] 解析 seatIds 失败: {}", seatIdsStr);
            return Collections.emptyList();
        }
    }
}