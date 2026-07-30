package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.biz.shared.service.MovieService;
import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.common.dal.mapper.*;
import com.alipay.ticketbacked.core.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SOFA AI Function Callbacks — 对应 Python agent/tools.py 的 8 个 @tool
 */
@Configuration
public class AgentFunctionConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ToolCallback cb(String name, String desc, String schema, Function<String, String> fn) {
        Function<Object, JsonNode> wrapper = input -> {
            try {
                String jsonInput = input instanceof String ? (String) input : MAPPER.writeValueAsString(input);
                String result = fn.apply(jsonInput);
                return MAPPER.readTree(result);
            } catch (Exception e) {
                return MAPPER.createObjectNode().put("error", "tool execution failed");
            }
        };
        return FunctionToolCallback.builder(name, (Function) wrapper)
                .description(desc)
                .inputSchema(schema)
                .inputType((Class) Object.class)
                .build();
    }

    @Bean public ToolCallback searchMovies(MovieMapper mm) {
        return cb("search_movies", "搜索电影列表，支持关键词/类型/排序。",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"搜索关键词\"},\"genre\":{\"type\":\"string\",\"description\":\"电影类型\"},\"sort_by\":{\"type\":\"string\",\"description\":\"排序方式: rating或release\",\"default\":\"rating\"},\"status\":{\"type\":\"string\",\"description\":\"上映状态: showing或coming\",\"default\":\"showing\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回数量(1-20)\",\"default\":10}},\"required\":[]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String kw = (String) a.get("keyword"), genre = (String) a.get("genre");
                String sort = (String) a.getOrDefault("sort_by","rating"), status = (String) a.getOrDefault("status","showing");
                int limit = Math.min(Math.max(a.containsKey("limit") ? ((Number)a.get("limit")).intValue() : 10, 1), 20);
                List<Movie> movies;
                if (genre != null && !genre.isBlank()) movies = mm.findByGenre(genre, status, limit);
                else if (kw != null && !kw.isBlank()) movies = mm.searchByKeyword(kw, status, limit);
                else movies = "release".equals(sort) ? mm.findByStatusOrderByRelease(status, limit) : mm.findByStatusOrderByRating(status, limit);
                List<Map<String,Object>> r = new ArrayList<>();
                for (Movie m : movies) { var i = new LinkedHashMap<String,Object>(); i.put("id",m.getId()); i.put("title",m.getTitle()); i.put("rating",m.getRating()); i.put("genre",m.getGenre()); i.put("duration",m.getDuration()); i.put("poster_url",m.getPosterUrl()); i.put("director",m.getDirector()); r.add(i); }
                return MAPPER.writeValueAsString(r);
            } catch (Exception e) { return "[{\"error\":\"搜索失败\"}]"; }
        });
    }

    @Bean public ToolCallback searchCinemas(CinemaMapper cm) {
        return cb("search_cinemas", "搜索影院列表，支持按名称模糊匹配和城市过滤。",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"影院名称关键词\"},\"city\":{\"type\":\"string\",\"description\":\"城市名称\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回数量(1-20)\",\"default\":10}},\"required\":[]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String kw = (String) a.get("keyword"), city = (String) a.get("city");
                int limit = Math.min(Math.max(a.containsKey("limit") ? ((Number)a.get("limit")).intValue() : 10, 1), 20);
                List<Cinema> cinemas = (city != null && !city.isBlank()) ? cm.findByCity(city.replace("市",""), limit) : cm.findAll(limit);
                if (kw != null && !kw.isBlank()) cinemas = cinemas.stream().filter(c -> c.getName().contains(kw)).collect(Collectors.toList());
                List<Map<String,Object>> r = new ArrayList<>();
                for (Cinema c : cinemas) { var i = new LinkedHashMap<String,Object>(); i.put("id",c.getId()); i.put("name",c.getName()); i.put("address",c.getAddress()); i.put("city",c.getCity()); i.put("phone",c.getPhone()); r.add(i); }
                return MAPPER.writeValueAsString(r);
            } catch (Exception e) { return "[{\"error\":\"搜索失败\"}]"; }
        });
    }

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Haversine 公式计算两点间距离（公里） */
    private static double haversineKm(double lng1, double lat1, double lng2, double lat2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    @Bean public ToolCallback searchSessions(MovieMapper mm, CinemaMapper cm, SessionMapper sm) {
        return cb("search_sessions", "查询某电影的排片场次。只返回用户定位5公里范围内的影院、且为当天及以后的场次。电影名和影院名支持模糊匹配。可指定具体日期过滤。",
                "{\"type\":\"object\",\"properties\":{\"movie_name\":{\"type\":\"string\",\"description\":\"电影名称\"},\"cinema_name\":{\"type\":\"string\",\"description\":\"影院名称(可选)\"},\"date\":{\"type\":\"string\",\"description\":\"指定日期 yyyy-MM-dd 格式(可选,如2026-07-15)\"},\"lat\":{\"type\":\"number\",\"description\":\"用户纬度(系统自动注入)\"},\"lng\":{\"type\":\"number\",\"description\":\"用户经度(系统自动注入)\"}},\"required\":[\"movie_name\"]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String movieName = (String) a.get("movie_name"), cinemaName = (String) a.get("cinema_name");
                String dateStr = (String) a.get("date");
                double userLat = a.containsKey("lat") ? ((Number) a.get("lat")).doubleValue() : -1;
                double userLng = a.containsKey("lng") ? ((Number) a.get("lng")).doubleValue() : -1;
                boolean hasLocation = userLat > 0 && userLng > 0;

                // 解析日期参数
                java.time.LocalDate filterDate = null;
                if (dateStr != null && !dateStr.isBlank()) {
                    try {
                        // 支持 yyyy-MM-dd 或 yyyy-MM-dd 后面跟时间段
                        String datePart = dateStr.trim().split("\\s+")[0];
                        filterDate = java.time.LocalDate.parse(datePart);
                    } catch (Exception ignored) {
                        // 日期解析失败，忽略不按日期过滤
                    }
                }

                List<Movie> mm2 = mm.searchByKeyword(movieName, "showing", 10);
                if (mm2.isEmpty()) mm2 = mm.searchByKeyword(movieName, "coming", 10);
                if (mm2.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到电影: "+movieName)));
                if (mm2.size() > 1) { List<String> t = mm2.stream().map(Movie::getTitle).collect(Collectors.toList()); return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation",true,"type","movie","options",t,"message","找到多部电影："+String.join(" / ",t)))); }
                Movie movie = mm2.get(0);

                // 日期过滤：只返回当天及以后的场次
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime farFuture = now.plusYears(1);

                // 影院过滤逻辑
                List<Session> sessions;
                if (cinemaName != null && !cinemaName.isBlank()) {
                    // 用户指定了影院名 —— 只是按该影院查
                    List<Cinema> cm2 = cm.searchByName(cinemaName, 5);
                    if (cm2.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到影院: "+cinemaName)));
                    if (cm2.size() > 1) { List<String> n = cm2.stream().map(Cinema::getName).collect(Collectors.toList()); return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation",true,"type","cinema","options",n,"message","找到多家影院："+String.join(" / ",n)))); }
                    sessions = sm.findByMovieAndCinema(movie.getId(), cm2.get(0).getId());
                    // 再按日期过滤
                    sessions = sessions.stream()
                            .filter(s -> s.getStartTime() != null && !s.getStartTime().isBefore(now))
                            .collect(Collectors.toList());
                } else if (hasLocation) {
                    // 未指定影院但有用户定位 —— 只查5km范围内的影院的场次
                    List<Cinema> allCinemas = cm.findAllNoLimit();
                    Set<Long> nearbyCinemaIds = allCinemas.stream()
                            .filter(c -> c.getLongitude() != null && c.getLatitude() != null)
                            .filter(c -> haversineKm(userLng, userLat,
                                    c.getLongitude().doubleValue(), c.getLatitude().doubleValue()) <= 5.0)
                            .map(Cinema::getId)
                            .collect(Collectors.toSet());
                    if (nearbyCinemaIds.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","您附近5公里内暂无影院有排片")));
                    // 查该电影的所有场次，再在内存按日期+附近影院过滤
                    sessions = sm.findByMovieIdAndDate(movie.getId(), now, farFuture);
                    sessions = sessions.stream()
                            .filter(s -> nearbyCinemaIds.contains(s.getCinemaId()))
                            .collect(Collectors.toList());
                } else {
                    // 无定位也未指定影院 —— 查所有当天及以后的场次
                    sessions = sm.findByMovieIdAndDate(movie.getId(), now, farFuture);
                }

                if (sessions.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到当天及以后的场次")));

                // 按指定日期过滤场次
                if (filterDate != null) {
                    java.time.LocalDate fDate = filterDate;
                    sessions = sessions.stream()
                            .filter(s -> s.getStartTime() != null
                                    && s.getStartTime().toLocalDate().equals(fDate))
                            .collect(java.util.stream.Collectors.toList());
                    if (sessions.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","该日期没有找到排片")));
                }

                // 构建 cinemaId -> Cinema 的映射
                Map<Long, Cinema> cinemaMap = cm.findAllNoLimit().stream()
                        .collect(Collectors.toMap(Cinema::getId, c -> c, (a1, b1) -> a1));

                List<Map<String,Object>> items = new ArrayList<>();
                for (Session s : sessions) {
                    var i = new LinkedHashMap<String,Object>();
                    i.put("id", s.getId());
                    i.put("start_time", s.getStartTime() != null ? s.getStartTime().toString() : "");
                    i.put("end_time", s.getEndTime() != null ? s.getEndTime().toString() : "");
                    i.put("price", s.getPrice());
                    i.put("status", s.getStatus());
                    i.put("movie_title", movie.getTitle());
                    Cinema cin = cinemaMap.get(s.getCinemaId());
                    i.put("cinema_id", s.getCinemaId());
                    i.put("cinema_name", cin != null ? cin.getName() : "");
                    items.add(i);
                }
                return MAPPER.writeValueAsString(items);
            } catch (Exception e) { return "[{\"error\":\"查询场次失败\"}]"; }
        });
    }

    @Bean public ToolCallback getUserOrders(OrderService os) {
        return cb("get_user_orders", "查询用户订单列表，可按状态筛选。",
                "{\"type\":\"object\",\"properties\":{\"user_id\":{\"type\":\"integer\",\"description\":\"用户ID\"},\"status_filter\":{\"type\":\"string\",\"description\":\"状态筛选: paid/unpaid/refunded\"}},\"required\":[\"user_id\"]}",
                input -> {
            try { Map<String,Object> a = MAPPER.readValue(input, Map.class); Long uid = ((Number)a.get("user_id")).longValue(); String sf = (String)a.get("status_filter");
                return MAPPER.writeValueAsString(os.listOrders(uid, sf)); } catch (Exception e) { return "[{\"error\":\"查询订单失败\"}]"; }
        });
    }

    // refund_order 已移除 — 退票由前端订单卡片按钮触发 GUI 退款流程，LLM 不执行退款操作。

    @Bean public ToolCallback getMovieDetail(MovieMapper mm) {
        return cb("get_movie_detail", "查询电影详细信息。",
                "{\"type\":\"object\",\"properties\":{\"movie_name\":{\"type\":\"string\",\"description\":\"电影名称\"}},\"required\":[\"movie_name\"]}",
                input -> {
            try { Map<String,Object> a = MAPPER.readValue(input, Map.class); String name = (String)a.get("movie_name");
                List<Movie> m = mm.searchByKeyword(name, "showing", 1); if (m.isEmpty()) m = mm.searchByKeyword(name, "coming", 1);
                if (m.isEmpty()) return "{\"error\":\"未找到电影\"}";
                Movie mv = m.get(0); var r = new LinkedHashMap<String,Object>(); r.put("id",mv.getId()); r.put("title",mv.getTitle()); r.put("rating",mv.getRating()); r.put("duration",mv.getDuration()); r.put("genre",mv.getGenre()); r.put("director",mv.getDirector()); r.put("actors",mv.getActors()); r.put("description",mv.getDescription()); r.put("poster_url",mv.getPosterUrl());
                return MAPPER.writeValueAsString(r); } catch (Exception e) { return "{\"error\":\"查询失败\"}"; }
        });
    }

    @Bean public ToolCallback recommendMovies(MovieService ms) {
        return cb("recommend_movies", "推荐正在热映的电影。",
                "{\"type\":\"object\",\"properties\":{\"genre\":{\"type\":\"string\",\"description\":\"电影类型(可选)\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回数量(1-10)\",\"default\":5}},\"required\":[]}",
                input -> {
            try { Map<String,Object> a = MAPPER.readValue(input, Map.class); String genre = (String)a.get("genre");
                int limit = Math.min(Math.max(a.containsKey("limit") ? ((Number)a.get("limit")).intValue() : 5, 1), 10);
                var dtos = ms.recommendMovies(genre, limit); List<Map<String,Object>> r = new ArrayList<>();
                for (var d : dtos) { var i = new LinkedHashMap<String,Object>(); i.put("id",d.getId()); i.put("title",d.getTitle()); i.put("rating",d.getRating()); i.put("genre",d.getGenre()); i.put("poster_url",d.getPosterUrl()); r.add(i); }
                return MAPPER.writeValueAsString(r); } catch (Exception e) { return "[{\"error\":\"推荐失败\"}]"; }
        });
    }

    @Bean public ToolCallback getCinemaInfo(CinemaMapper cm, HallMapper hm) {
        return cb("get_cinema_info", "查询影院详细信息，包括地址、电话、影厅类型(是否有IMAX/VIP厅)、影厅数量。用于回答用户关于影院设施的问题。",
                "{\"type\":\"object\",\"properties\":{\"cinema_name\":{\"type\":\"string\",\"description\":\"影院名称关键词\"},\"city\":{\"type\":\"string\",\"description\":\"城市名称(可选，用于消歧)\"}},\"required\":[\"cinema_name\"]}",
                input -> {
            try {
                Map<String, Object> a = MAPPER.readValue(input, Map.class);
                String kw = (String) a.get("cinema_name");
                String city = (String) a.get("city");

                // 1. 查影院
                List<Cinema> cinemas;
                if (city != null && !city.isBlank()) {
                    cinemas = cm.findByCity(city.replace("市", ""), 20);
                    if (kw != null && !kw.isBlank()) {
                        cinemas = cinemas.stream().filter(c -> c.getName().contains(kw)).collect(Collectors.toList());
                    }
                } else {
                    cinemas = cm.searchByName(kw, 5);
                }
                if (cinemas.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error", "未找到影院: " + kw)));
                if (cinemas.size() > 1) {
                    List<String> names = cinemas.stream().map(Cinema::getName).collect(Collectors.toList());
                    return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation", true, "type", "cinema", "options", names, "message", "找到多家影院：" + String.join(" / ", names))));
                }

                Cinema cinema = cinemas.get(0);

                // 2. 查影厅
                List<Hall> halls = hm.findByCinemaId(cinema.getId());
                boolean hasImax = halls.stream().anyMatch(h -> "imax".equalsIgnoreCase(h.getHallType()));
                boolean hasVip = halls.stream().anyMatch(h -> "vip".equalsIgnoreCase(h.getHallType()));
                boolean hasNormal = halls.stream().anyMatch(h -> "normal".equalsIgnoreCase(h.getHallType()) || h.getHallType() == null);

                // 3. 统计影厅类型分布
                Map<String, Long> typeCount = halls.stream()
                        .collect(Collectors.groupingBy(
                                h -> h.getHallType() != null ? h.getHallType() : "normal",
                                Collectors.counting()));

                // 4. 构建返回
                Map<String, Object> info = new LinkedHashMap<>();
                info.put("name", cinema.getName());
                info.put("address", cinema.getAddress());
                info.put("phone", cinema.getPhone());
                info.put("city", cinema.getCity());
                info.put("hall_count", halls.size());
                info.put("has_imax", hasImax);
                info.put("has_vip", hasVip);
                info.put("has_normal", hasNormal);
                info.put("hall_types", typeCount);

                // 影厅明细
                List<Map<String, Object>> hallList = new ArrayList<>();
                for (Hall h : halls) {
                    Map<String, Object> hi = new LinkedHashMap<>();
                    hi.put("name", h.getName());
                    hi.put("type", h.getHallType() != null ? h.getHallType() : "normal");
                    hi.put("rows", h.getTotalRows());
                    hi.put("cols", h.getTotalCols());
                    hallList.add(hi);
                }
                info.put("halls", hallList);

                return MAPPER.writeValueAsString(info);
            } catch (Exception e) {
                return "{\"error\":\"查询影院信息失败\"}";
            }
        });
    }

    @Bean public ToolCallback saveSlots(ChatSessionMapper csm) {
        return cb("save_slots", "保存购票信息槽位，供跨轮记忆。",
                "{\"type\":\"object\",\"properties\":{\"session_id\":{\"type\":\"string\",\"description\":\"会话ID\"},\"movie_name\":{\"type\":\"string\",\"description\":\"电影名\"},\"cinema_name\":{\"type\":\"string\",\"description\":\"影院名\"},\"time_expression\":{\"type\":\"string\",\"description\":\"时间表达\"},\"ticket_count\":{\"type\":\"string\",\"description\":\"购票数量\"}},\"required\":[\"session_id\"]}",
                input -> {
            try { Map<String,Object> a = MAPPER.readValue(input, Map.class); String sid = (String)a.get("session_id");
                if (sid != null) { ChatSession s = csm.findBySessionId(sid); if (s != null) {
                    Map<String,Object> slots = new HashMap<>(); for (String k : List.of("movie_name","cinema_name","time_expression","ticket_count")) if (a.get(k) != null) slots.put(k, a.get(k));
                    @SuppressWarnings("unchecked") Map<String,Object> existing = s.getSlots() != null && !s.getSlots().isBlank() ? MAPPER.readValue(s.getSlots(), Map.class) : new HashMap<>(); existing.putAll(slots);
                    csm.updateSlotsAndContext(sid, MAPPER.writeValueAsString(existing), "", s.getContext() != null ? s.getContext() : "{}"); } }
                return "{\"status\":\"ok\",\"message\":\"槽位已保存\"}"; } catch (Exception e) { return "{\"status\":\"error\"}"; }
        });
    }
}