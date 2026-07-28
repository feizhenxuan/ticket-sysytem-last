package com.alipay.ticketbacked.core.model;

import java.time.LocalDateTime;

/**
 * 对话会话实体 — 对应 hx_chat_sessions 表
 * slots/context/messages 用 TEXT 存储 JSON 字符串。
 */
public class ChatSession {
    private Long id;
    private String sessionId;
    private Integer userId;
    private String slots;      // JSON 字符串
    private String lastIntent;
    private String context;    // JSON 字符串
    private String messages;   // JSON 字符串数组
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModify;
    private LocalDateTime gmtExpire;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getSlots() { return slots; }
    public void setSlots(String slots) { this.slots = slots; }
    public String getLastIntent() { return lastIntent; }
    public void setLastIntent(String lastIntent) { this.lastIntent = lastIntent; }
    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }
    public String getMessages() { return messages; }
    public void setMessages(String messages) { this.messages = messages; }
    public LocalDateTime getGmtCreate() { return gmtCreate; }
    public void setGmtCreate(LocalDateTime gmtCreate) { this.gmtCreate = gmtCreate; }
    public LocalDateTime getGmtModify() { return gmtModify; }
    public void setGmtModify(LocalDateTime gmtModify) { this.gmtModify = gmtModify; }
    public LocalDateTime getGmtExpire() { return gmtExpire; }
    public void setGmtExpire(LocalDateTime gmtExpire) { this.gmtExpire = gmtExpire; }
}