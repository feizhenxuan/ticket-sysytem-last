package com.alipay.ticketbacked.core.model;

import java.time.LocalDateTime;

/**
 * 场次座位状态实体 — 对应 hx_session_seats 表
 */
public class SessionSeat {
    private Long id;
    private Long sessionId;
    private Long seatId;
    private String status; // available / locked / sold
    private Long lockedByOrderId;
    private LocalDateTime lockedAt;
    private LocalDateTime gmtModify;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLockedByOrderId() { return lockedByOrderId; }
    public void setLockedByOrderId(Long lockedByOrderId) { this.lockedByOrderId = lockedByOrderId; }
    public LocalDateTime getLockedAt() { return lockedAt; }
    public void setLockedAt(LocalDateTime lockedAt) { this.lockedAt = lockedAt; }
    public LocalDateTime getGmtModify() { return gmtModify; }
    public void setGmtModify(LocalDateTime gmtModify) { this.gmtModify = gmtModify; }
}