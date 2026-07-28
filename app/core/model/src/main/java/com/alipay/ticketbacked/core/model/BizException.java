package com.alipay.ticketbacked.core.model;

/**
 * 业务异常 — 供 biz/shared 和 web 层共同使用。
 * 由 GlobalExceptionHandler 捕获并转为对应 HTTP 状态码。
 */
public class BizException extends RuntimeException {

    private final int status;

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }

    public static BizException notFound(String detail) {
        return new BizException(404, detail);
    }

    public static BizException badRequest(String detail) {
        return new BizException(400, detail);
    }

    public static BizException unauthorized(String detail) {
        return new BizException(401, detail);
    }

    public int getStatus() { return status; }
}
