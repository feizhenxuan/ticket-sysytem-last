package com.alipay.ticketbacked.core.model;

import java.time.LocalDateTime;

/**
 * 影厅实体 — 对应 hx_halls 表
 */
public class Hall {
    private Long id;
    private Long cinemaId;
    private String name;
    private String hallType; // normal / imax / vip
    private Integer totalRows;
    private Integer totalCols;
    private LocalDateTime gmtCreate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCinemaId() { return cinemaId; }
    public void setCinemaId(Long cinemaId) { this.cinemaId = cinemaId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHallType() { return hallType; }
    public void setHallType(String hallType) { this.hallType = hallType; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getTotalCols() { return totalCols; }
    public void setTotalCols(Integer totalCols) { this.totalCols = totalCols; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
}