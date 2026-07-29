package com.alipay.ticketbacked.core.model.dto;

/**
 * 场次座位状态 DTO — 对应 Python schemas/session.py SessionSeatItem
 */
public class SessionSeatDTO {
    private Long seatId;
    private Integer row;
    private Integer col;
    private String type;   // normal / couple / vip
    private String status; // available / locked / sold

    public Long getSeatId() { return seatId; }
    public void setSeatId(Long seatId) { this.seatId = seatId; }
    public Integer getRow() { return row; }
    public void setRow(Integer row) { this.row = row; }
    public Integer getCol() { return col; }
    public void setCol(Integer col) { this.col = col; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}