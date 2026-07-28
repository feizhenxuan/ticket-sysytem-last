package com.alipay.ticketbacked.core.model.dto;

import java.time.LocalDateTime;

/**
 * 用户信息响应 DTO
 */
public class UserResponse {
    private Long id;
    private String username;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}