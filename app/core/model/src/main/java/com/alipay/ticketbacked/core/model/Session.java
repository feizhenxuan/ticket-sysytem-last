package com.alipay.ticketbacked.core.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 场次实体 — 对应 hx_sessions 表
 */
public class Session {
    private Long id;
    private Long movieId;
    private Long cinemaId;
    private Long hallId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal price;
    private String status; // available / sold_out / closed
    private LocalDateTime gmtCreate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMovieId() { return movieId; }
    public void setMovieId(Long movieId) { this.movieId = movieId; }
    public Long getCinemaId() { return cinemaId; }
    public void setCinemaId(Long cinemaId) { this.cinemaId = cinemaId; }
    public Long getHallId() { return hallId; }
    public void setHallId(Long hallId) { this.hallId = hallId; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}