package com.alipay.ticketbacked.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体 — 对应 hx_orders 表
 * seat_ids 在 OceanBase 中用 TEXT 存储 JSON 数组字符串，如 "[1,2,3]"
 */
public class Order {
    private Long id;
    private String orderNo;
    private Long userId;
    private Long sessionId;
    private String seatIds; // JSON 字符串 "[1,2,3]"
    private Integer ticketCount;
    private BigDecimal totalAmount;
    private String status; // pending / paid / cancelled / refunded
    private String tradeNo;
    private String pickupCode;
    private LocalDateTime gmtCreate;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime refundedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public String getSeatIds() { return seatIds; }
    public void setSeatIds(String seatIds) { this.seatIds = seatIds; }
    public Integer getTicketCount() { return ticketCount; }
    public void setTicketCount(Integer ticketCount) { this.ticketCount = ticketCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getTradeNo() { return tradeNo; }
    public void setTradeNo(String tradeNo) { this.tradeNo = tradeNo; }
    public String getPickupCode() { return pickupCode; }
    public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getRefundedAt() { return refundedAt; }
    public void setRefundedAt(LocalDateTime refundedAt) { this.refundedAt = refundedAt; }
}