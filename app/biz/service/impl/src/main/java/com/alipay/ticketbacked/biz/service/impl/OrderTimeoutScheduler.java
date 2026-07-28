package com.alipay.ticketbacked.biz.service.impl;

import com.alipay.ticketbacked.common.dal.mapper.OrderMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.core.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
            sessionSeatMapper.releaseSeatsByOrderId(order.getId());
        }
        if (!expired.isEmpty()) {
            log.info("[Scheduler] 已取消 {} 个超时订单", expired.size());
        }
    }
}