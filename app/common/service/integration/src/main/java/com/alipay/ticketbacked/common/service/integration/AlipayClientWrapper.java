package com.alipay.ticketbacked.common.service.integration;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
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
     *  pageExecute().getBody() 返回自提交 HTML <form>，前端需用 document.write 渲染 */
    public String createPaymentForm(String orderNo, String amount, String subject) {
        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        request.setReturnUrl(returnUrl);
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
            throw new RuntimeException("创建支付表单失败: " + e.getMessage());
        }
    }

    /** 验签支付宝异步通知 — 对应 Python verify_notify */
    public boolean verifyNotify(Map<String, String> params) {
        try {
            String sign = params.remove("sign");
            params.remove("sign_type");
            return AlipaySignature.rsaCheckV2(params, alipayPublicKey, "UTF-8", "RSA2");
        } catch (AlipayApiException e) {
            log.error("验签失败", e);
            return false;
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