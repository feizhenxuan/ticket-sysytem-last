package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.common.dal.mapper.*;
import com.alipay.ticketbacked.core.model.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 管理后台 - 统计面板 — 对应 Python api/admin_stats.py
 * 补齐前端期望的 overview / revenue-trend / movie-ranking / order-status-distribution
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    private final OrderMapper orderMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final SessionMapper sessionMapper;
    private final UserMapper userMapper;
    private final HallMapper hallMapper;

    public AdminStatsController(OrderMapper orderMapper, MovieMapper movieMapper,
                                CinemaMapper cinemaMapper, SessionMapper sessionMapper,
                                UserMapper userMapper, HallMapper hallMapper) {
        this.orderMapper = orderMapper;
        this.movieMapper = movieMapper;
        this.cinemaMapper = cinemaMapper;
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
        this.hallMapper = hallMapper;
    }

    /** 基础统计（聚合 SQL 版，不再全表拉内存） */
    @GetMapping
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        // 订单各状态数量：一条 GROUP BY status（替代 3 次 findAllForAdmin(...).size() 全表拉行）
        for (Map<String, Object> row : orderMapper.countByStatus()) {
            String status = (String) row.get("status");
            int cnt = toInt(row.get("cnt"));
            switch (status) {
                case "pending":  stats.put("pending_orders", cnt); break;
                case "paid":     stats.put("paid_orders", cnt); break;
                case "refunded": stats.put("refunded_orders", cnt); break;
                default: break;
            }
        }
        // 在映影片数：一条 GROUP BY（替代 findByStatusOrderByRating("showing",9999).size()）
        int showing = 0;
        for (Map<String, Object> row : movieMapper.countByStatus()) {
            if ("showing".equals(row.get("status"))) {
                showing = toInt(row.get("cnt"));
            }
        }
        stats.put("movies_count", showing);
        stats.put("cinemas_count", cinemaMapper.countAll());
        Map<String, Object> sc = sessionMapper.countSummary();
        stats.put("sessions_count", toInt(sc == null ? null : sc.get("total")));
        return stats;
    }

    /** 前端期望: /api/admin/stats/overview —— 全部改为 SQL 聚合，杜绝全表拉内存与 N+1 */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        // ---- 电影：一条 GROUP BY status（替代 2 次 findByStatusOrderByRating(...).size()） ----
        int showing = 0, coming = 0;
        for (Map<String, Object> row : movieMapper.countByStatus()) {
            String s = (String) row.get("status");
            int cnt = toInt(row.get("cnt"));
            if ("showing".equals(s)) {
                showing = cnt;
            } else if ("coming".equals(s)) {
                coming = cnt;
            }
        }
        overview.put("movies", Map.of("total", showing + coming, "showing", showing));

        // ---- 影院 / 影厅：两条独立 COUNT(*)（替代「遍历影院 + 每个影院查 halls」的 N+1） ----
        overview.put("cinemas", Map.of("total", cinemaMapper.countAll(), "halls", hallMapper.countAll()));

        // ---- 场次：一条 SQL 同时拿 total 与 available（替代 findAllAvailable() 全表拉行再内存 filter） ----
        Map<String, Object> sc = sessionMapper.countSummary();
        overview.put("sessions", Map.of(
                "total", toInt(sc == null ? null : sc.get("total")),
                "available", toInt(sc == null ? null : sc.get("available"))));

        // ---- 订单 + 收入：一条 GROUP BY status（替代 99 万行全表拉内存 + 4 次 stream 遍历） ----
        int totalOrders = 0, paid = 0, pending = 0, refunded = 0;
        double revenue = 0;
        for (Map<String, Object> row : orderMapper.statsSummary()) {
            String s = (String) row.get("status");
            int cnt = toInt(row.get("cnt"));
            totalOrders += cnt;
            revenue += toDouble(row.get("revenue"));
            switch (s) {
                case "paid":     paid = cnt; break;
                case "pending":  pending = cnt; break;
                case "refunded": refunded = cnt; break;
                default: break;
            }
        }
        overview.put("orders", Map.of("total", totalOrders, "paid", paid, "pending", pending, "refunded", refunded));
        overview.put("revenue", Math.round(revenue * 100) / 100.0);

        // ---- 用户：一条 COUNT(*)（替代 findAllForAdmin(null,null,999999,0).size() 全表拉行） ----
        overview.put("users", userMapper.countAll());

        return overview;
    }

    /** 安全数值转换：MyBatis 聚合返回的可能是 Long/Integer/BigDecimal/NULL */
    private static int toInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private static double toDouble(Object o) {
        return o == null ? 0 : ((Number) o).doubleValue();
    }

    /** 前端期望: /api/admin/stats/revenue-trend */
    @GetMapping("/revenue-trend")
    public Map<String, Object> revenueTrend(@RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = orderMapper.dailyRevenueTrend(days);
        return Map.of("items", trend, "days", days);
    }

    /** 前端期望: /api/admin/stats/movie-ranking */
    @GetMapping("/movie-ranking")
    public Map<String, Object> movieRanking(@RequestParam(defaultValue = "5") int limit) {
        List<Map<String, Object>> ranking = orderMapper.movieRanking(limit);
        return Map.of("items", ranking);
    }

    /** 前端期望: /api/admin/stats/order-status-distribution */
    @GetMapping("/order-status-distribution")
    public Map<String, Object> orderStatusDist() {
        List<Map<String, Object>> dist = orderMapper.countByStatus();
        return Map.of("items", dist);
    }
}