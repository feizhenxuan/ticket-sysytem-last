package com.alipay.ticketbacked.common.service.integration;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝沙箱客户端 — 对应 Python utils/alipay_client.py
 */
@Component
public class AlipayClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(AlipayClientWrapper.class);

    @Value("${app.alipay.app-id}")
    private String appId;

    @Value("${app.alipay.app-private-key}")
    private String appPrivateKey;

    @Value("${app.alipay.public-key}")
    private String alipayPublicKey;

    @Value("${app.alipay.gateway}")
    private String gateway;

    @Value("${app.alipay.return-url}")
    private String returnUrl;

    @Value("${app.alipay.notify-url:}")
    private String notifyUrl;

    private AlipayClient client;

    private AlipayClient getClient() {
        if (client == null) {
            client = new DefaultAlipayClient(
                    gateway, appId, appPrivateKey, "json", "UTF-8", alipayPublicKey, "RSA2"
            );
        }
        return client;
    }

    /** 创建支付宝支付表单 HTML — 对应 Python create_payment_url
     *  pageExecute().getBody() 返回自提交 HTML <form>，前端需用 document.write 渲染
     *  @param returnUrlOverride 前端动态传入的 return_url（优先使用，避免硬编码端口不匹配） */
    public String createPaymentForm(String orderNo, String amount, String subject, String returnUrlOverride) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        String effectiveReturnUrl = (returnUrlOverride != null && !returnUrlOverride.isBlank())
                ? returnUrlOverride : returnUrl;
        request.setReturnUrl(effectiveReturnUrl);
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            request.setNotifyUrl(notifyUrl);
        }
        request.setBizContent(
                "{\"out_trade_no\":\"" + orderNo + "\","
                + "\"total_amount\":\"" + amount + "\","
                + "\"subject\":\"" + subject + "\","
                + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"}");
        try {
            return getClient().pageExecute(request).getBody();
        } catch (AlipayApiException e) {
            log.error("创建支付表单失败", e);
            throw new RuntimeException("创建支付表单失败: " + e.getMessage(), e);
        }
    }

    /** 兼容旧调用（使用配置文件中的 return_url） */
    public String createPaymentForm(String orderNo, String amount, String subject) {
        return createPaymentForm(orderNo, amount, subject, null);
    }

    /** 当面付预下单 — 返回二维码链接（沙箱支持）
     *  前端拿到 qr_code 后生成二维码图片，用户用支付宝扫码支付 */
    public Map<String, Object> createPrecreateQRCode(String orderNo, String amount, String subject) {
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        if (notifyUrl != null && !notifyUrl.isBlank()) {
            request.setNotifyUrl(notifyUrl);
        }
        request.setBizContent(
                "{\"out_trade_no\":\"" + orderNo + "\","
                + "\"total_amount\":\"" + amount + "\","
                + "\"subject\":\"" + subject + "\"}");
        try {
            AlipayTradePrecreateResponse response = getClient().execute(request);
            Map<String, Object> result = new HashMap<>();
            result.put("code", response.getCode());
            result.put("msg", response.getMsg());
            result.put("qr_code", response.getQrCode());
            result.put("sub_msg", response.getSubMsg());
            return result;
        } catch (AlipayApiException e) {
            log.error("预下单失败, orderNo={}", orderNo, e);
            Map<String, Object> result = new HashMap<>();
            result.put("code", "40004");
            result.put("sub_msg", e.getMessage());
            result.put("qr_code", null);
            return result;
        }
    }

    /** 验签支付宝异步通知 — 对应 Python verify_notify */
    public boolean verifyNotify(Map<String, String> params) {
        try {
            params.remove("sign");
            params.remove("sign_type");
            return AlipaySignature.rsaCheckV2(params, alipayPublicKey, "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            log.error("验签失败", e);
            return false;
        }
    }

    /** 查询支付宝交易状态 — 用于取消/刷新订单前确认是否已付款
     *  @return Map 包含 trade_status: TRADE_SUCCESS=已付款, WAIT_BUYER_PAY=等待付款,
     *           TRADE_CLOSED=已关闭/未付款关闭, TRADE_FINISHED=交易完成（不可退款）
     *           query_failed=true 表示查询本身出错（网络/签名等），调用方应自行决定兜底策略 */
    public Map<String, Object> queryTradeStatus(String orderNo) {
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{\"out_trade_no\":\"" + orderNo + "\"}");
        try {
            AlipayTradeQueryResponse response = getClient().execute(request);
            Map<String, Object> result = new HashMap<>();
            result.put("code", response.getCode());
            result.put("msg", response.getMsg());
            result.put("trade_status", response.getTradeStatus());
            result.put("trade_no", response.getTradeNo());
            result.put("total_amount", response.getTotalAmount());
            result.put("query_failed", false);
            return result;
        } catch (AlipayApiException e) {
            log.error("查询支付宝交易状态失败, orderNo={}", orderNo, e);
            Map<String, Object> result = new HashMap<>();
            result.put("query_failed", true);
            result.put("trade_status", null);
            return result;
        }
    }

    /** 退款 — 对应 Python refund */
    public Map<String, Object> refund(String orderNo, String amount) {
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent(
                "{\"out_trade_no\":\"" + orderNo + "\","
                + "\"refund_amount\":\"" + amount + "\","
                + "\"refund_reason\":\"用户退票\"}");
        try {
            AlipayTradeRefundResponse response = getClient().execute(request);
            Map<String, Object> result = new HashMap<>();
            result.put("code", response.getCode());
            result.put("msg", response.getMsg());
            result.put("sub_msg", response.getSubMsg());
            return result;
        } catch (AlipayApiException e) {
            log.error("退款失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("code", "40004");
            result.put("sub_msg", e.getMessage());
            return result;
        }
    }
}