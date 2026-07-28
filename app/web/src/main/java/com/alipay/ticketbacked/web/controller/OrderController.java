package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.core.model.Order;
import com.alipay.ticketbacked.core.model.User;
import com.alipay.ticketbacked.core.model.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 订单接口 — 对应 Python /api/orders
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public Order createOrder(
            @RequestParam Long session_id,
            @RequestParam List<Long> seat_ids,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        return orderService.createOrder(user.getId(), session_id, seat_ids);
    }

    @GetMapping
    public Map<String, Object> listOrders(
            @RequestParam(name = "status", required = false) String status,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        List<Map<String, Object>> items = orderService.listOrders(user.getId(), status);
        return Map.of("items", items, "total", items.size());
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        Order order = orderService.getOrder(id, user.getId());
        if (order == null) throw BizException.notFound("订单不存在");
        return order;
    }

    @PostMapping("/{id}/cancel")
    public Map<String, Object> cancelOrder(@PathVariable Long id, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        orderService.cancelOrder(id, user.getId());
        return Map.of("message", "订单已取消");
    }
}