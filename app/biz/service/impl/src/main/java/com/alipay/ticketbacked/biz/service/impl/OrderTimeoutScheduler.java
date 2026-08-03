package com.alipay.ticketbacked.biz.service.impl;

import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.common.dal.mapper.OrderMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.common.service.integration.AlipayClientWrapper;
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
import java.util.Map;

/**
 * 定时任务 — 对应 Python core/scheduler.py
 * 每30秒扫描，5分钟未支付的订单：查支付宝确认是否已付款
 *  已付款 → 确认为 paid（生成取票码、标记座位已售）
 *  未付款 → 取消订单并释放座位
 *  查询失败 → 跳过本轮，等下一轮再查
 */
@Component
public class OrderTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutScheduler.class);

    private final OrderMapper orderMapper;
    private final SessionSeatMapper sessionSeatMapper;
    private final AlipayClientWrapper alipayClient;
    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OrderTimeoutScheduler(OrderMapper orderMapper, SessionSeatMapper sessionSeatMapper,
                                 AlipayClientWrapper alipayClient, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.sessionSeatMapper = sessionSeatMapper;
        this.alipayClient = alipayClient;
        this.orderService = orderService;
    }

    @Scheduled(fixedRate = 30000)
    @Transactional
    public void cancelExpiredOrders() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(5);
        List<Order> expired = orderMapper.findExpiredPendingOrders(expiryTime);
        int confirmedCount = 0;
        int cancelledCount = 0;
        int skippedCount = 0;
        for (Order order : expired) {
            // 取消前先查支付宝，确认这笔订单是否已经付款
            Map<String, Object> queryResult = alipayClient.queryTradeStatus(order.getOrderNo());
            boolean queryFailed = Boolean.TRUE.equals(queryResult.get("query_failed"));
            String tradeStatus = (String) queryResult.get("trade_status");

            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                // 用户已经付款了！直接确认为已支付（生成取票码、标记座位为已售）
                log.info("[Scheduler] 订单 {} 在支付宝侧已付款(trade_status={}), 确认为已支付", order.getId(), tradeStatus);
                String tradeNo = (String) queryResult.get("trade_no");
                orderService.checkAndConfirmPayment(order);
                confirmedCount++;
            } else if (queryFailed) {
                // 查询失败，跳过本轮，等下一轮再查
                log.warn("[Scheduler] 订单 {} 查询支付宝状态失败，跳过本轮取消", order.getId());
                skippedCount++;
            } else {
                // 未付款，正常取消并释放座位
                log.info("[Scheduler] 订单 {} 支付宝侧未付款(trade_status={}), 正常取消", order.getId(), tradeStatus);
                orderMapper.updateCancelStatus(order.getId(), "cancelled", LocalDateTime.now());
                releaseSeatsForOrder(order);
                cancelledCount++;
            }
        }
        if (!expired.isEmpty()) {
            log.info("[Scheduler] 本轮处理 {} 个超时订单: 确认支付 {}, 取消 {}, 跳过 {}", expired.size(), confirmedCount, cancelledCount, skippedCount);
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