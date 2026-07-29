package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.common.dal.mapper.OrderMapper;
import com.alipay.ticketbacked.core.model.Order;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理后台 - 订单管理 — 对应 Python api/admin_orders.py
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final OrderMapper orderMapper;
    private final OrderService orderService;

    public AdminOrderController(OrderMapper orderMapper, OrderService orderService) {
        this.orderMapper = orderMapper;
        this.orderService = orderService;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "20") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        List<Order> orders = orderMapper.findAllForAdmin(status, Math.min(limit, 100), offset);
        return Map.of("items", orders, "total", orders.size());
    }

    @GetMapping("/{id}")
    public Order detail(@PathVariable Long id) {
        Order order = orderMapper.findById(id);
        if (order == null) throw BizException.notFound("订单不存在");
        return order;
    }

    @PostMapping("/{id}/refund")
    public Map<String, Object> refund(@PathVariable Long id) {
        Order order = orderMapper.findById(id);
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"paid".equals(order.getStatus())) throw BizException.badRequest("只能退已支付订单");
        orderService.refundOrder(id, order.getUserId());
        return Map.of("success", true, "message", "退款成功");
    }
}