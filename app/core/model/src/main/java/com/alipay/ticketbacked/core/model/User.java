package com.alipay.ticketbacked.core.model;

import java.time.LocalDateTime;

/**
 * 用户实体 — 对应 hx_users 表
 */
public class User {
    private Long id;
    private String username;
    private String passwordHash;
    private Boolean isActive;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModify;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModify() { return gmtModify; }
    public void setGmtModify(LocalDateTime gmtModify) { this.gmtModify = gmtModify; }
}