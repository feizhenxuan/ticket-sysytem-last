package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.common.service.integration.AlipayClientWrapper;
import com.alipay.ticketbacked.core.model.Order;
import com.alipay.ticketbacked.core.model.User;
import com.alipay.ticketbacked.core.model.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付接口 — 对应 Python /api/pay
 */
@RestController
@RequestMapping("/api/pay")
public class PaymentController {

    private final OrderService orderService;
    private final AlipayClientWrapper alipayClient;

    public PaymentController(OrderService orderService, AlipayClientWrapper alipayClient) {
        this.orderService = orderService;
        this.alipayClient = alipayClient;
    }

    @GetMapping("/create")
    public Map<String, Object> createPayment(@RequestParam Long order_id, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        Order order = orderService.getOrder(order_id, user.getId());
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"pending".equals(order.getStatus())) throw BizException.badRequest("订单状态不可支付");

        // 查电影名作为支付标题
        String subject = "电影票-" + order.getOrderNo();
        String payForm = alipayClient.createPaymentForm(
                order.getOrderNo(),
                order.getTotalAmount().toPlainString(),
                subject
        );
        return Map.of("pay_form", payForm);
    }

    @GetMapping("/verify")
    public Map<String, Object> payVerify(
            @RequestParam String out_trade_no,
            @RequestParam String trade_no,
            @RequestParam String total_amount) {
        return orderService.confirmPayment(out_trade_no, trade_no);
    }

    @PostMapping("/notify")
    public String payNotify() {
        return "success";
    }

    @PostMapping("/refund")
    public Map<String, Object> refund(@RequestParam Long order_id, HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        Order order = orderService.getOrder(order_id, user.getId());
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"paid".equals(order.getStatus())) throw BizException.badRequest("只能退已支付订单");

        Map<String, Object> result = alipayClient.refund(order.getOrderNo(), order.getTotalAmount().toPlainString());
        String code = (String) result.get("code");
        if ("10000".equals(code)) {
            orderService.refundOrder(order_id, user.getId());
            return Map.of("success", true, "message", "退款成功");
        }
        Map<String, Object> fail = new HashMap<>();
        fail.put("success", false);
        fail.put("message", result.getOrDefault("sub_msg", "退款失败"));
        return fail;
    }
}