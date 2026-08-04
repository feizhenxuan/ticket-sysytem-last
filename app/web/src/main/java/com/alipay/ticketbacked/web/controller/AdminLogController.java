package com.alipay.ticketbacked.web.controller;
import com.alipay.ticketbacked.common.dal.mapper.AdminLogMapper;
import com.alipay.ticketbacked.core.model.AdminLog;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/admin/logs")
public class AdminLogController {
    private final AdminLogMapper adminLogMapper;
    public AdminLogController(AdminLogMapper adminLogMapper) { this.adminLogMapper = adminLogMapper; }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String module,
                                    @RequestParam(defaultValue = "20") int limit,
                                    @RequestParam(defaultValue = "0") int offset) {
        List<AdminLog> logs; int total;
        if (module != null && !module.isBlank()) { logs = adminLogMapper.findByModule(module, Math.min(limit, 200), offset); total = adminLogMapper.countByModule(module); }
        else { logs = adminLogMapper.findAll(Math.min(limit, 200), offset); total = adminLogMapper.countAll(); }
        List<Map<String, Object>> items = new ArrayList<>();
        for (AdminLog l : logs) { Map<String,Object> m = new LinkedHashMap<>();
            m.put("id",l.getId()); m.put("admin_id",l.getAdminId()); m.put("admin_username",l.getAdminUsername());
            m.put("module",l.getModule()); m.put("action",l.getAction()); m.put("target_id",l.getTargetId());
            m.put("target_name",l.getTargetName()); m.put("request_path",l.getRequestPath());
            m.put("request_method",l.getRequestMethod()); m.put("status",l.getStatus());
            m.put("detail",l.getDetail()); m.put("gmt_create",l.getGmtCreate()); items.add(m); }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("items", items); result.put("total", total);
        return result;
    }
}
