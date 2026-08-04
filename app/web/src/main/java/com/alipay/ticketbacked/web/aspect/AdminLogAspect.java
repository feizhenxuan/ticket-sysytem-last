package com.alipay.ticketbacked.web.aspect;
import com.alipay.ticketbacked.common.dal.mapper.AdminLogMapper;
import com.alipay.ticketbacked.core.model.Admin;
import com.alipay.ticketbacked.core.model.AdminLog;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Map;

@Aspect
@Component
public class AdminLogAspect {
    private static final Logger log = LoggerFactory.getLogger(AdminLogAspect.class);
    private final AdminLogMapper adminLogMapper;
    public AdminLogAspect(AdminLogMapper adminLogMapper) { this.adminLogMapper = adminLogMapper; }

    @Around("execution(* com.alipay.ticketbacked.web.controller.Admin*.*(..))")
    public Object logAdminAction(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return joinPoint.proceed();
        HttpServletRequest request = attrs.getRequest();
        String httpMethod = request.getMethod();
        if ("GET".equalsIgnoreCase(httpMethod) || "OPTIONS".equalsIgnoreCase(httpMethod)) return joinPoint.proceed();

        Admin admin = (Admin) request.getAttribute("currentAdmin");
        Object result = null; String status = "success"; String detail = null;
        try { result = joinPoint.proceed(); return result; }
        catch (Throwable e) { status = "fail"; detail = e.getMessage() != null ? e.getMessage().substring(0, Math.min(e.getMessage().length(), 500)) : "unknown"; throw e; }
        finally {
            try {
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String methodName = joinPoint.getSignature().getName();
                String requestPath = request.getRequestURI();
                AdminLog adminLog = new AdminLog();
                if (admin != null) { adminLog.setAdminId(admin.getId()); adminLog.setAdminUsername(admin.getUsername()); }
                adminLog.setModule(resolveModule(className));
                adminLog.setAction(resolveAction(methodName, httpMethod));
                adminLog.setTargetId(extractTargetId(requestPath));
                adminLog.setTargetName(extractTargetName(result));
                adminLog.setRequestPath(requestPath); adminLog.setRequestMethod(httpMethod);
                adminLog.setStatus(status); adminLog.setDetail(detail != null ? detail : methodName);
                adminLogMapper.insert(adminLog);
            } catch (Exception e) { log.warn("[AdminLogAspect] 记录日志失败: {}", e.getMessage()); }
        }
    }
    private String resolveModule(String c) {
        if (c.contains("Movie")) return "电影管理"; if (c.contains("Cinema")) return "影院管理";
        if (c.contains("Session")) return "场次管理"; if (c.contains("Order")) return "订单管理";
        if (c.contains("User")) return "用户管理"; if (c.contains("Config")) return "系统配置";
        if (c.contains("Auth")) return "管理员认证"; return "其他";
    }
    private String resolveAction(String m, String h) {
        String l = m.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("refund")) return "退款"; if (l.contains("register")) return "注册"; if (l.contains("login")) return "登录";
        if (l.contains("status")) return "修改状态";
        if (l.contains("create") || "POST".equals(h)) return l.contains("hall") ? "新增影厅" : "新增";
        if (l.contains("update") || "PUT".equals(h)) return l.contains("hall") ? "编辑影厅" : "编辑";
        if (l.contains("delete") || "DELETE".equals(h)) return l.contains("hall") ? "删除影厅" : "删除";
        return h + " " + m;
    }
    private Long extractTargetId(String path) { for (String p : path.split("/")) { try { return Long.parseLong(p); } catch (Exception ignored) {
        // This catch statement is intentionally empty
    } } return null; }
    private String extractTargetName(Object r) { if (r instanceof Map) { Map<?,?> m = (Map<?,?>)r; Object n = m.get("title"); if (n==null) n=m.get("name"); if (n==null) n=m.get("message"); if (n!=null) return n.toString(); } return null; }
}
