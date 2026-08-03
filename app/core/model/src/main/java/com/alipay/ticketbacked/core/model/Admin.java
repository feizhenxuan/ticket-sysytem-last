package com.alipay.ticketbacked.core.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;

/**
 * 管理员实体 — 对应 hx_admins 表
 */
public class Admin {
    private Long id;
    private String username;
    @JsonIgnore
    private String passwordHash;
    private String role;
    private Boolean isActive;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModify;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModify() { return gmtModify; }
    public void setGmtModify(LocalDateTime gmtModify) { this.gmtModify = gmtModify; }
}