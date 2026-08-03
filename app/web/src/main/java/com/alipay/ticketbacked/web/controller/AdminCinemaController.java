package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.CinemaService;
import com.alipay.ticketbacked.common.dal.mapper.HallMapper;
import com.alipay.ticketbacked.common.dal.mapper.SeatMapper;
import com.alipay.ticketbacked.common.dal.mapper.CinemaMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionMapper;
import com.alipay.ticketbacked.common.dal.mapper.SessionSeatMapper;
import com.alipay.ticketbacked.core.model.Cinema;
import com.alipay.ticketbacked.core.model.Hall;
import com.alipay.ticketbacked.core.model.Seat;
import com.alipay.ticketbacked.core.model.Session;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * 管理后台 - 影院管理 + 影厅管理 — 对应 Python api/admin_cinemas.py
 */
@RestController
@RequestMapping("/api/admin/cinemas")
public class AdminCinemaController {

    private final CinemaService cinemaService;
    private final HallMapper hallMapper;
    private final SeatMapper seatMapper;
    private final CinemaMapper cinemaMapper;
    private final SessionMapper sessionMapper;
    private final SessionSeatMapper sessionSeatMapper;

    public AdminCinemaController(CinemaService cinemaService, HallMapper hallMapper,
                                  SeatMapper seatMapper, CinemaMapper cinemaMapper,
                                  SessionMapper sessionMapper, SessionSeatMapper sessionSeatMapper) {
        this.cinemaService = cinemaService;
        this.hallMapper = hallMapper;
        this.seatMapper = seatMapper;
        this.cinemaMapper = cinemaMapper;
        this.sessionMapper = sessionMapper;
        this.sessionSeatMapper = sessionSeatMapper;
    }

    @GetMapping
    public Object list(@RequestParam(required = false) String search,
                       @RequestParam(required = false) String city,
                       @RequestParam(defaultValue = "50") int limit,
                       @RequestParam(defaultValue = "0") int offset) {
        List<Cinema> cinemas = cinemaMapper.findAll(9999);
        if (search != null && !search.isBlank()) {
            String kw = search.toLowerCase();
            cinemas = cinemas.stream().filter(c ->
                (c.getName() != null && c.getName().toLowerCase().contains(kw)) ||
                (c.getAddress() != null && c.getAddress().toLowerCase().contains(kw))
            ).collect(Collectors.toList());
        }
        if (city != null && !city.isBlank()) {
            cinemas = cinemas.stream().filter(c -> city.equals(c.getCity())).collect(Collectors.toList());
        }
        int total = cinemas.size();
        cinemas = cinemas.stream().skip(offset).limit(Math.min(limit, 9999)).collect(Collectors.toList());
        var items = cinemas.stream().map(c -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", c.getId());
            map.put("name", c.getName());
            map.put("address", c.getAddress());
            map.put("longitude", c.getLongitude());
            map.put("latitude", c.getLatitude());
            map.put("phone", c.getPhone());
            map.put("city", c.getCity());
            map.put("hall_count", hallMapper.findByCinemaId(c.getId()).size());
            return map;
        }).collect(Collectors.toList());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        return result;
    }

    @GetMapping("/{id}")
    public Object detail(@PathVariable Long id) {
        var dto = cinemaService.getCinema(id);
        if (dto == null) throw BizException.notFound("影院不存在");
        return dto;
    }

    @PostMapping
    public Object create(@RequestBody Cinema cinema) {
        cinemaService.createCinema(cinema);
        return cinema;
    }

    @PutMapping("/{id}")
    public Object update(@PathVariable Long id, @RequestBody Cinema cinema) {
        cinema.setId(id);
        cinemaService.updateCinema(cinema);
        return cinema;
    }

    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {
        cinemaService.deleteCinema(id);
        return Map.of("message", "删除成功");
    }

    // ===== Halls =====

    @GetMapping("/{cinemaId}/halls")
    public Object listHalls(@PathVariable Long cinemaId) {
        List<Hall> halls = hallMapper.findByCinemaId(cinemaId);
        var items = halls.stream().map(h -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", h.getId());
            map.put("cinema_id", h.getCinemaId());
            map.put("name", h.getName());
            map.put("hall_type", h.getHallType());
            map.put("total_rows", h.getTotalRows());
            map.put("total_cols", h.getTotalCols());
            map.put("seat_count", (h.getTotalRows() != null && h.getTotalCols() != null) ? h.getTotalRows() * h.getTotalCols() : 0);
            return map;
        }).collect(Collectors.toList());
        return Map.of("items", items, "total", items.size());
    }

    @PostMapping("/{cinemaId}/halls")
    public Object createHall(@PathVariable Long cinemaId, @RequestBody Hall hall) {
        hall.setCinemaId(cinemaId);
        if (hall.getHallType() == null) hall.setHallType("normal");
        if (hall.getTotalRows() == null) hall.setTotalRows(8);
        if (hall.getTotalCols() == null) hall.setTotalCols(12);
        hallMapper.insert(hall);
        return hall;
    }

    @PutMapping("/halls/{hallId}")
    public Object updateHall(@PathVariable Long hallId, @RequestBody Hall hall) {
        hall.setId(hallId);
        Hall existing = hallMapper.findById(hallId);
        if (existing == null) throw BizException.notFound("影厅不存在");
        if (hall.getCinemaId() == null) hall.setCinemaId(existing.getCinemaId());
        if (hall.getName() == null) hall.setName(existing.getName());
        if (hall.getHallType() == null) hall.setHallType(existing.getHallType());
        if (hall.getTotalRows() == null) hall.setTotalRows(existing.getTotalRows());
        if (hall.getTotalCols() == null) hall.setTotalCols(existing.getTotalCols());
        hallMapper.update(hall);
        return hall;
    }

    @DeleteMapping("/halls/{hallId}")
    public Object deleteHall(@PathVariable Long hallId) {
        hallMapper.deleteById(hallId);
        return Map.of("message", "删除成功");
    }

    /**
     * 批量初始化座位 — 为指定城市的所有影厅生成座位
     * GET /api/admin/cinemas/init-seats?city=北京
     */
    @GetMapping("/init-seats")
    public Object initSeats(@RequestParam String city, @RequestParam(required = false) Long cinema_id) {
        List<Cinema> cinemas = cinemaMapper.findAll(9999);
        int hallCount = 0;
        int seatCount = 0;

        for (Cinema cinema : cinemas) {
            if (!city.equals(cinema.getCity())) continue;
            if (cinema_id != null && !cinema_id.equals(cinema.getId())) continue;

            List<Hall> halls = hallMapper.findByCinemaId(cinema.getId());
            for (Hall hall : halls) {
                // 检查是否已有座位
                List<Seat> existing = seatMapper.findByHallId(hall.getId());
                if (existing != null && !existing.isEmpty()) continue;

                int rows = hall.getTotalRows() != null ? hall.getTotalRows() : 8;
                int cols = hall.getTotalCols() != null ? hall.getTotalCols() : 12;

                seatCount += seatMapper.initSeatsForHall(hall.getId(), rows, cols);
                hallCount++;
            }
        }

        return Map.of("city", city, "halls", hallCount, "seats", seatCount);
    }

    /**
     * 超级批量初始化 — 一次调用完成一个城市的全部数据:
     * 影厅 + 座位 + 场次 + 场次座位状态
     * POST /api/admin/cinemas/batch-init?city=郑州&dates=2026-08-01,2026-08-02,...
     */
    @PostMapping("/batch-init")
    public Object batchInit(@RequestParam String city,
                            @RequestParam(required = false) String dates,
                            @RequestParam(required = false, defaultValue = "2026-07-30") String date,
                            @RequestParam(required = false, defaultValue = "2026-07-31") String date2) {
        List<Cinema> allCinemas = cinemaMapper.findAll(9999);
        int hallCount = 0, seatCount = 0, sessionCount = 0, seatStatusCount = 0;

        // 7部电影：安昂传奇、肖申克、挽救计划、痴迷、迈克尔杰克逊、超级马力欧、星际穿越
        Long[] movieIds = {17L, 50L, 44L, 4L, 54L, 56L, 27L};
        // 6个时间段
        String[][] timeSlots = {
            {"10:00:00", "12:00:00", "38.0"},
            {"12:30:00", "14:30:00", "42.0"},
            {"15:00:00", "17:00:00", "45.0"},
            {"17:30:00", "19:30:00", "48.0"},
            {"20:00:00", "22:00:00", "52.0"},
            {"22:30:00", "00:30:00", "46.0"}
        };

        // 解析日期列表
        String[] dateList;
        if (dates != null && !dates.isEmpty()) {
            dateList = dates.split(",");
        } else {
            dateList = new String[]{date, date2};
        }

        for (Cinema cinema : allCinemas) {
            if (!city.equals(cinema.getCity())) continue;

            // 1. 查已有影厅，没有就创建
            List<Hall> halls = hallMapper.findByCinemaId(cinema.getId());
            if (halls == null || halls.isEmpty()) {
                Hall hall = new Hall();
                hall.setCinemaId(cinema.getId());
                hall.setName("1号厅");
                hall.setHallType("normal");
                hall.setTotalRows(8);
                hall.setTotalCols(12);
                hallMapper.insert(hall);
                halls = new ArrayList<>();
                halls.add(hall);
                hallCount++;
            }

            Hall hall = halls.get(0);
            int rows = hall.getTotalRows() != null ? hall.getTotalRows() : 8;
            int cols = hall.getTotalCols() != null ? hall.getTotalCols() : 12;

            // 2. 批量创建座位（一条SQL）
            List<Seat> existingSeats = seatMapper.findByHallId(hall.getId());
            if (existingSeats == null || existingSeats.isEmpty()) {
                seatCount += seatMapper.initSeatsForHall(hall.getId(), rows, cols);
            }

            // 3. 创建场次（每天6场，7部电影轮流分配）
            int mi = 0;
            for (String d : dateList) {
                for (String[] slot : timeSlots) {
                    // 处理跨日场次（22:30 -> 00:30 次日）
                    String endDate = d;
                    if (slot[1].compareTo(slot[0]) < 0) {
                        // end time is next day
                        java.time.LocalDate nextDay = java.time.LocalDate.parse(d).plusDays(1);
                        endDate = nextDay.toString();
                    }
                    Session session = new Session();
                    session.setMovieId(movieIds[mi % movieIds.length]);
                    session.setCinemaId(cinema.getId());
                    session.setHallId(hall.getId());
                    session.setStartTime(java.time.LocalDateTime.parse(d + "T" + slot[0]));
                    session.setEndTime(java.time.LocalDateTime.parse(endDate + "T" + slot[1]));
                    session.setPrice(new java.math.BigDecimal(slot[2]));
                    session.setStatus("available");
                    try {
                        sessionMapper.insert(session);
                        sessionCount++;
                        // 4. 批量创建场次座位状态（一条SQL）
                        sessionSeatMapper.initSeatsForSession(session.getId(), hall.getId());
                        seatStatusCount += rows * cols;
                    } catch (Exception ignored) {
                        // This catch statement is intentionally empty
                    }
                    mi++;
                }
            }
        }

        return Map.of("city", city, "halls", hallCount, "seats", seatCount,
                       "sessions", sessionCount, "seat_statuses", seatStatusCount);
    }
}