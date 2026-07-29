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

    /** 基础统计（原有） */
    @GetMapping
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("pending_orders", orderMapper.findAllForAdmin("pending", 9999, 0).size());
        stats.put("paid_orders", orderMapper.findAllForAdmin("paid", 9999, 0).size());
        stats.put("refunded_orders", orderMapper.findAllForAdmin("refunded", 9999, 0).size());
        stats.put("movies_count", movieMapper.findByStatusOrderByRating("showing", 9999).size());
        stats.put("cinemas_count", cinemaMapper.findAll(9999).size());
        stats.put("sessions_count", sessionMapper.findAllAvailable().size());
        return stats;
    }

    /** 前端期望: /api/admin/stats/overview */
    @GetMapping("/overview")
    public Map<String, Object> overview() {
        Map<String, Object> overview = new LinkedHashMap<>();

        int showingCount = movieMapper.findByStatusOrderByRating("showing", 9999).size();
        int totalMovies = showingCount + movieMapper.findByStatusOrderByRating("coming", 9999).size();
        overview.put("movies", Map.of("total", totalMovies, "showing", showingCount));

        List<Cinema> cinemas = cinemaMapper.findAll(9999);
        int hallsCount = 0;
        for (Cinema c : cinemas) hallsCount += hallMapper.findByCinemaId(c.getId()).size();
        overview.put("cinemas", Map.of("total", cinemas.size(), "halls", hallsCount));

        List<Session> sessions = sessionMapper.findAllAvailable();
        long available = sessions.stream().filter(s -> "available".equals(s.getStatus())).count();
        overview.put("sessions", Map.of("total", sessions.size(), "available", (int) available));

        List<Order> allOrders = orderMapper.findAllForAdmin(null, 999999, 0);
        long paid = allOrders.stream().filter(o -> "paid".equals(o.getStatus())).count();
        long pending = allOrders.stream().filter(o -> "pending".equals(o.getStatus())).count();
        long refunded = allOrders.stream().filter(o -> "refunded".equals(o.getStatus())).count();
        double revenue = allOrders.stream()
                .filter(o -> "paid".equals(o.getStatus()))
                .mapToDouble(o -> o.getTotalAmount() != null ? o.getTotalAmount().doubleValue() : 0)
                .sum();
        overview.put("orders", Map.of("total", allOrders.size(), "paid", (int) paid, "pending", (int) pending, "refunded", (int) refunded));
        overview.put("revenue", Math.round(revenue * 100) / 100.0);
        overview.put("users", userMapper.findAllForAdmin(null, null, 999999, 0).size());

        return overview;
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