package com.alipay.ticketbacked.core.model;
import java.time.LocalDateTime;
public class AdminLog {
    private Long id; private Long adminId; private String adminUsername;
    private String module; private String action; private Long targetId;
    private String targetName; private String requestPath; private String requestMethod;
    private String status; private String detail; private LocalDateTime gmtCreate;
    public Long getId() { return id; } public void setId(Long id) { this.id = id; }
    public Long getAdminId() { return adminId; } public void setAdminId(Long adminId) { this.adminId = adminId; }
    public String getAdminUsername() { return adminUsername; } public void setAdminUsername(String s) { this.adminUsername = s; }
    public String getModule() { return module; } public void setModule(String s) { this.module = s; }
    public String getAction() { return action; } public void setAction(String s) { this.action = s; }
    public Long getTargetId() { return targetId; } public void setTargetId(Long v) { this.targetId = v; }
    public String getTargetName() { return targetName; } public void setTargetName(String s) { this.targetName = s; }
    public String getRequestPath() { return requestPath; } public void setRequestPath(String s) { this.requestPath = s; }
    public String getRequestMethod() { return requestMethod; } public void setRequestMethod(String s) { this.requestMethod = s; }
    public String getStatus() { return status; } public void setStatus(String s) { this.status = s; }
    public String getDetail() { return detail; } public void setDetail(String s) { this.detail = s; }
    public LocalDateTime getGmtCreate() { return gmtCreate; } public void setGmtCreate(LocalDateTime v) { this.gmtCreate = v; }
}
