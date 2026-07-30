package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.common.service.integration.AlipayClientWrapper;
import com.alipay.ticketbacked.core.model.Order;
import com.alipay.ticketbacked.core.model.User;
import com.alipay.ticketbacked.core.model.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付接口 — 对应 Python /api/pay
 */
@RestController
@RequestMapping("/api/pay")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final OrderService orderService;
    private final AlipayClientWrapper alipayClient;

    public PaymentController(OrderService orderService, AlipayClientWrapper alipayClient) {
        this.orderService = orderService;
        this.alipayClient = alipayClient;
    }

    @GetMapping("/create")
    public Map<String, Object> createPayment(
            @RequestParam Long order_id,
            @RequestParam(required = false) String return_url,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        Order order = orderService.getOrder(order_id, user.getId());
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"pending".equals(order.getStatus())) throw BizException.badRequest("订单状态不可支付");

        String subject = "电影票-" + order.getOrderNo();
        String payForm = alipayClient.createPaymentForm(
                order.getOrderNo(),
                order.getTotalAmount().toPlainString(),
                subject,
                return_url
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
    public String payNotify(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });

        log.info("[payNotify] 收到支付宝异步通知: trade_no={}, out_trade_no={}, trade_status={}",
                params.get("trade_no"), params.get("out_trade_no"), params.get("trade_status"));

        // 验签
        if (!alipayClient.verifyNotify(params)) {
            log.warn("[payNotify] 验签失败: out_trade_no={}", params.get("out_trade_no"));
            return "fail";
        }

        // 只处理交易成功状态
        String tradeStatus = params.get("trade_status");
        if (!"TRADE_SUCCESS".equals(tradeStatus) && !"TRADE_FINISHED".equals(tradeStatus)) {
            log.info("[payNotify] 非成功交易状态，忽略: {}", tradeStatus);
            return "success";
        }

        String orderNo = params.get("out_trade_no");
        String tradeNo = params.get("trade_no");
        Map<String, Object> result = orderService.confirmPayment(orderNo, tradeNo);
        log.info("[payNotify] 订单确认结果: orderNo={}, result={}", orderNo, result);

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