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
        if (!alipayClient.isConfigured()) {
            throw BizException.badRequest("支付宝沙箱未配置，无法创建支付表单");
        }

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
        try {
            return orderService.confirmPayment(out_trade_no, trade_no);
        } catch (Exception e) {
            // confirmPayment 可能因 DB 连接超时等抛异常，但订单可能已更新为 paid
            // 重新查一次订单状态，如果确实已支付，返回成功
            log.warn("[payVerify] confirmPayment 异常，回查订单状态: out_trade_no={}", out_trade_no, e);
            Order order = orderService.findByOrderNo(out_trade_no);
            if (order != null && "paid".equals(order.getStatus())) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "支付成功");
                result.put("order_no", out_trade_no);
                result.put("pickup_code", order.getPickupCode() != null ? order.getPickupCode() : "");
                result.put("status", "paid");
                return result;
            }
            throw e;
        }
    }

    /** 当面付预下单 — 返回二维码链接，前端生成二维码图片 */
    @GetMapping("/qrcode")
    public Map<String, Object> createQRCode(
            @RequestParam Long order_id,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        Order order = orderService.getOrder(order_id, user.getId());
        if (order == null) throw BizException.notFound("订单不存在");
        if (!"pending".equals(order.getStatus())) throw BizException.badRequest("订单状态不可支付");

        String subject = "电影票-" + order.getOrderNo();
        Map<String, Object> result = alipayClient.createPrecreateQRCode(
                order.getOrderNo(),
                order.getTotalAmount().toPlainString(),
                subject
        );
        String code = (String) result.get("code");
        if ("10000".equals(code)) {
            log.info("[qrcode] 预下单成功: orderId={}, orderNo={}", order_id, order.getOrderNo());
            Map<String, Object> success = new HashMap<>();
            success.put("success", true);
            success.put("qr_code", result.get("qr_code"));
            success.put("order_no", order.getOrderNo());
            return success;
        }
        log.warn("[qrcode] 预下单失败: orderId={}, code={}, sub_msg={}", order_id, code, result.get("sub_msg"));
        Map<String, Object> fail = new HashMap<>();
        fail.put("success", false);
        fail.put("message", result.getOrDefault("sub_msg", "预下单失败"));
        return fail;
    }

    /** 查询订单支付状态 — 前端轮询用，后端查支付宝确认是否已付款 */
    @GetMapping("/status")
    public Map<String, Object> payStatus(
            @RequestParam Long order_id,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        Order order = orderService.getOrder(order_id, user.getId());
        if (order == null) throw BizException.notFound("订单不存在");

        // 如果已经是 paid 状态直接返回
        if ("paid".equals(order.getStatus())) {
            Map<String, Object> result = new HashMap<>();
            result.put("status", "paid");
            result.put("success", true);
            result.put("pickup_code", order.getPickupCode() != null ? order.getPickupCode() : "");
            return result;
        }

        // 查支付宝确认
        orderService.checkAndConfirmPayment(order);

        // 重新查一次订单
        Order updated = orderService.getOrder(order_id, user.getId());
        Map<String, Object> result = new HashMap<>();
        if (updated != null && "paid".equals(updated.getStatus())) {
            result.put("status", "paid");
            result.put("success", true);
            result.put("pickup_code", updated.getPickupCode() != null ? updated.getPickupCode() : "");
        } else {
            result.put("status", "pending");
            result.put("success", false);
        }
        return result;
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

        log.info("[refund] 开始退款: orderId={}, orderNo={}, amount={}", order_id, order.getOrderNo(), order.getTotalAmount());
        Map<String, Object> result = alipayClient.refund(order.getOrderNo(), order.getTotalAmount().toPlainString());
        String code = (String) result.get("code");
        if ("10000".equals(code)) {
            orderService.refundOrder(order_id, user.getId());
            log.info("[refund] 退款成功: orderId={}", order_id);
            return Map.of("success", true, "message", "退款成功");
        }
        log.warn("[refund] 支付宝退款失败: orderId={}, code={}, sub_msg={}", order_id, code, result.get("sub_msg"));
        Map<String, Object> fail = new HashMap<>();
        fail.put("success", false);
        fail.put("message", result.getOrDefault("sub_msg", "退款失败"));
        return fail;
    }
}
