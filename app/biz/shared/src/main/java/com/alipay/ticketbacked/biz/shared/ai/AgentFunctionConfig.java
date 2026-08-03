package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.biz.shared.service.MovieService;
import com.alipay.ticketbacked.biz.shared.service.OrderService;
import com.alipay.ticketbacked.common.dal.mapper.*;
import com.alipay.ticketbacked.core.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * SOFA AI Function Callbacks — 对应 Python agent/tools.py 的 8 个 @tool
 */
@Configuration
public class AgentFunctionConfig {

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Logger log = LoggerFactory.getLogger(AgentFunctionConfig.class);

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
        return cb("search_movies", "搜索电影列表，支持关键词/类型/排序。sort_by 可选 rating(评分高)、release(最近上映)、price(价格低，按最低票价排序)。",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"搜索关键词\"},\"genre\":{\"type\":\"string\",\"description\":\"电影类型\"},\"sort_by\":{\"type\":\"string\",\"description\":\"排序方式: rating(评分高)或release(最近上映)或price(价格低)\",\"default\":\"rating\"},\"status\":{\"type\":\"string\",\"description\":\"上映状态: showing或coming\",\"default\":\"showing\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回数量(1-20)\",\"default\":10}},\"required\":[]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String kw = (String) a.get("keyword"), genre = (String) a.get("genre");
                String sort = (String) a.getOrDefault("sort_by","rating"), status = (String) a.getOrDefault("status","showing");
                int limit = Math.min(Math.max(a.containsKey("limit") ? ((Number)a.get("limit")).intValue() : 10, 1), 20);
                List<Movie> movies;
                if (genre != null && !genre.isBlank()) movies = mm.findByGenre(genre, status, limit);
                else if (kw != null && !kw.isBlank()) movies = mm.searchByKeyword(kw, status, limit);
                else if ("price".equals(sort)) movies = mm.findOrderByMinPrice(status, limit);
                else movies = "release".equals(sort) ? mm.findByStatusOrderByRelease(status, limit) : mm.findByStatusOrderByRating(status, limit);
                List<Map<String,Object>> r = new ArrayList<>();
                for (Movie m : movies) { var i = new LinkedHashMap<String,Object>(); i.put("id",m.getId()); i.put("title",m.getTitle()); i.put("rating",m.getRating()); i.put("genre",m.getGenre()); i.put("duration",m.getDuration()); i.put("poster_url",m.getPosterUrl()); i.put("director",m.getDirector()); r.add(i); }
                return MAPPER.writeValueAsString(r);
            } catch (Exception e) { return "[{\"error\":\"搜索失败\"}]"; }
        });
    }

    @Bean public ToolCallback searchCinemas(CinemaMapper cm) {
        return cb("search_cinemas", "搜索影院列表，支持按名称模糊匹配、城市过滤和附近定位搜索。当用户问'附近影院'时会自动注入经纬度，按距离排序返回。",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\",\"description\":\"影院名称关键词\"},\"city\":{\"type\":\"string\",\"description\":\"城市名称\"},\"lat\":{\"type\":\"number\",\"description\":\"用户纬度（系统自动注入）\"},\"lng\":{\"type\":\"number\",\"description\":\"用户经度（系统自动注入）\"},\"limit\":{\"type\":\"integer\",\"description\":\"返回数量(1-20)\",\"default\":10}},\"required\":[]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String kw = (String) a.get("keyword"), city = (String) a.get("city");
                int limit = Math.min(Math.max(a.containsKey("limit") ? ((Number)a.get("limit")).intValue() : 10, 1), 20);
                double userLat = a.containsKey("lat") ? ((Number)a.get("lat")).doubleValue() : 0;
                double userLng = a.containsKey("lng") ? ((Number)a.get("lng")).doubleValue() : 0;
                boolean hasLocation = userLat > 0 && userLng > 0;

                // 有定位时取全部影院做距离过滤；无定位时按城市或取全部
                List<Cinema> cinemas;
                if (hasLocation) {
                    cinemas = cm.findAllNoLimit();
                } else if (city != null && !city.isBlank()) {
                    cinemas = cm.findByCityKeyword(city.replace("市", ""), 200);
                } else {
                    cinemas = cm.findAll(limit);
                }

                if (kw != null && !kw.isBlank()) cinemas = cinemas.stream().filter(c -> c.getName().contains(kw)).collect(Collectors.toList());

                // 有定位时按距离过滤和排序
                if (hasLocation) {
                    final double fLat = userLat, fLng = userLng;
                    // 先计算距离、过滤10km以内、按距离排序
                    List<Map<String,Object>> r = cinemas.stream()
                        .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                        .map(c -> {
                            double dist = haversineKm(fLng, fLat, c.getLongitude().doubleValue(), c.getLatitude().doubleValue());
                            var i = new LinkedHashMap<String,Object>();
                            i.put("id", c.getId()); i.put("name", c.getName());
                            i.put("address", c.getAddress()); i.put("city", c.getCity());
                            i.put("phone", c.getPhone());
                            i.put("distance_km", Math.round(dist * 10) / 10.0);
                            return Map.entry(dist, i);
                        })
                        .filter(e -> e.getKey() <= 10.0)  // 10km范围内的影院
                        .sorted(Comparator.comparingDouble(Map.Entry::getKey))
                        .limit(limit)
                        .map(Map.Entry::getValue)
                        .collect(Collectors.toList());

                    if (r.isEmpty()) {
                        // 10km内没有影院，返回最近的5个
                        r = cinemas.stream()
                            .filter(c -> c.getLatitude() != null && c.getLongitude() != null)
                            .map(c -> {
                                double dist = haversineKm(fLng, fLat, c.getLongitude().doubleValue(), c.getLatitude().doubleValue());
                                var i = new LinkedHashMap<String,Object>();
                                i.put("id", c.getId()); i.put("name", c.getName());
                                i.put("address", c.getAddress()); i.put("city", c.getCity());
                                i.put("phone", c.getPhone());
                                i.put("distance_km", Math.round(dist * 10) / 10.0);
                                return Map.entry(dist, i);
                            })
                            .sorted(Comparator.comparingDouble(Map.Entry::getKey))
                            .limit(5)
                            .map(Map.Entry::getValue)
                            .collect(Collectors.toList());
                    }
                    return MAPPER.writeValueAsString(r);
                }

                // 无定位：保持原逻辑
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

    @Bean public ToolCallback searchSessions(MovieMapper mm, CinemaMapper cm, SessionMapper sm, HallMapper hm) {
        return cb("search_sessions", "查询排片场次。只返回用户定位5公里范围内的影院、且为当天及以后的场次。电影名和影院名支持模糊匹配。可指定具体日期过滤。当用户问'价格低的/便宜的'时，传 sort_by=price 且可不传 movie_name，会返回附近最低价场次。当用户指定了时间段(如下午4点)时，传 time_preference 参数，会优先返回最接近该时间的场次并高亮标记。",
                "{\"type\":\"object\",\"properties\":{\"movie_name\":{\"type\":\"string\",\"description\":\"电影名称(可选，不传时需传sort_by=price查最低价场次)\"},\"cinema_name\":{\"type\":\"string\",\"description\":\"影院名称(可选)\"},\"date\":{\"type\":\"string\",\"description\":\"指定日期 yyyy-MM-dd 格式(可选,如2026-07-15)\"},\"sort_by\":{\"type\":\"string\",\"description\":\"排序方式: price(按票价升序)，默认不排序按时间\"},\"time_preference\":{\"type\":\"string\",\"description\":\"用户期望的时间偏好，如'16:00'或'下午4点'，系统会筛选该时间前后2小时的场次并按接近度排序\"},\"lat\":{\"type\":\"number\",\"description\":\"用户纬度(系统自动注入)\"},\"lng\":{\"type\":\"number\",\"description\":\"用户经度(系统自动注入)\"}},\"required\":[]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String movieName = (String) a.get("movie_name"), cinemaName = (String) a.get("cinema_name");
                String dateStr = (String) a.get("date");
                String sortBy = (String) a.get("sort_by");
                String timePref = (String) a.get("time_preference");
                double userLat = a.containsKey("lat") ? ((Number) a.get("lat")).doubleValue() : -1;
                double userLng = a.containsKey("lng") ? ((Number) a.get("lng")).doubleValue() : -1;
                boolean hasLocation = userLat > 0 && userLng > 0;

                // 解析日期参数
                java.time.LocalDate filterDate = null;
                if (dateStr != null && !dateStr.isBlank()) {
                    try {
                        String datePart = dateStr.trim().split("\\s+")[0];
                        filterDate = java.time.LocalDate.parse(datePart);
                    } catch (Exception e) {
                        log.debug("[search_sessions] 日期参数解析失败: {}", dateStr);
                    }
                }

                // 解析 time_preference 为目标时间（用于按接近度排序+高亮）
                Integer targetHour = null;
                Integer targetMinute = null;
                if (timePref != null && !timePref.isBlank()) {
                    // 尝试解析 "16:00" / "下午4点" / "4点" / "16点" / "下午4:00" 等格式
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                            "(?:(?:下午|傍晚)\\s*)?(\\d{1,2})[:点：](\\d{2})?"
                    ).matcher(timePref);
                    if (m.find()) {
                        int h = Integer.parseInt(m.group(1));
                        int min = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                        // 下午/傍晚 且小时<=12 → +12
                        if (timePref.contains("下午") || timePref.contains("傍晚")) {
                            if (h <= 12) h += 12;
                        }
                        if (h >= 0 && h < 24 && min >= 0 && min < 60) {
                            targetHour = h;
                            targetMinute = min;
                        }
                    }
                }

                LocalDateTime now = LocalDateTime.now();
                LocalDateTime farFuture = now.plusYears(1);

                // 构建 cinemaId -> Cinema 的映射（复用）
                Map<Long, Cinema> cinemaMap = cm.findAllNoLimit().stream()
                        .collect(Collectors.toMap(Cinema::getId, c -> c, (a1, b1) -> a1));

                // ===== 无电影名 + sort_by=price：查附近最低价场次 =====
                if ((movieName == null || movieName.isBlank()) && "price".equals(sortBy)) {
                    if (!hasLocation) return MAPPER.writeValueAsString(List.of(Map.of("error","无法获取您的位置，请允许定位后重试")));
                    // 5km范围内影院ID
                    Set<Long> nearbyCinemaIds = cinemaMap.values().stream()
                            .filter(c -> c.getLongitude() != null && c.getLatitude() != null)
                            .filter(c -> haversineKm(userLng, userLat,
                                    c.getLongitude().doubleValue(), c.getLatitude().doubleValue()) <= 5.0)
                            .map(Cinema::getId)
                            .collect(Collectors.toSet());
                    if (nearbyCinemaIds.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","您附近5公里内暂无影院")));

                    // 查所有当天及以后的可用场次，按价格升序
                    List<Session> allSessions = sm.findAllAvailableOrderByPrice(now, 200);
                    // 过滤附近影院
                    List<Session> nearby = allSessions.stream()
                            .filter(s -> nearbyCinemaIds.contains(s.getCinemaId()))
                            .collect(Collectors.toList());
                    // 日期过滤
                    if (filterDate != null) {
                        java.time.LocalDate fDate = filterDate;
                        nearby = nearby.stream()
                                .filter(s -> s.getStartTime() != null && s.getStartTime().toLocalDate().equals(fDate))
                                .collect(Collectors.toList());
                    }
                    if (nearby.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","附近暂无可用场次")));
                    // 限制返回10条
                    nearby = nearby.stream().limit(10).collect(Collectors.toList());

                    // 构建 movieId -> Movie 映射
                    Map<Long, String> movieTitleMap = new HashMap<>();
                    for (Session s : nearby) {
                        if (!movieTitleMap.containsKey(s.getMovieId())) {
                            Movie m = mm.findById(s.getMovieId());
                            movieTitleMap.put(s.getMovieId(), m != null ? m.getTitle() : "未知电影");
                        }
                    }

                    List<Map<String,Object>> items = new ArrayList<>();
                    for (Session s : nearby) {
                        var i = new LinkedHashMap<String,Object>();
                        i.put("id", s.getId());
                        i.put("start_time", s.getStartTime() != null ? s.getStartTime().toString() : "");
                        i.put("end_time", s.getEndTime() != null ? s.getEndTime().toString() : "");
                        i.put("price", s.getPrice());
                        i.put("status", s.getStatus());
                        i.put("movie_title", movieTitleMap.getOrDefault(s.getMovieId(), "未知电影"));
                        Cinema cin = cinemaMap.get(s.getCinemaId());
                        i.put("cinema_id", s.getCinemaId());
                        i.put("cinema_name", cin != null ? cin.getName() : "");
                        items.add(i);
                    }
                    return MAPPER.writeValueAsString(items);
                }

                // ===== 无电影名：查询附近所有场次（按时间排序，最多返回10条） =====
                if (movieName == null || movieName.isBlank()) {
                    List<Session> nearbySessions;
                    if (hasLocation) {
                        List<Cinema> allCinemas = cm.findAllNoLimit();
                        Set<Long> nearbyCinemaIds = allCinemas.stream()
                                .filter(c -> c.getLongitude() != null && c.getLatitude() != null)
                                .filter(c -> haversineKm(userLng, userLat,
                                        c.getLongitude().doubleValue(), c.getLatitude().doubleValue()) <= 5.0)
                                .map(Cinema::getId)
                                .collect(Collectors.toSet());
                        if (nearbyCinemaIds.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","您附近5公里内暂无影院有排片")));
                        nearbySessions = sm.findAllAvailable();
                        if (nearbySessions == null) nearbySessions = java.util.Collections.emptyList();
                        nearbySessions = nearbySessions.stream()
                                .filter(s -> s.getStartTime() != null && !s.getStartTime().isBefore(now))
                                .filter(s -> nearbyCinemaIds.contains(s.getCinemaId()))
                                .collect(Collectors.toList());
                    } else {
                        nearbySessions = sm.findAllAvailable();
                        if (nearbySessions == null) nearbySessions = java.util.Collections.emptyList();
                        nearbySessions = nearbySessions.stream()
                                .filter(s -> s.getStartTime() != null && !s.getStartTime().isBefore(now))
                                .collect(Collectors.toList());
                    }
                    nearbySessions = nearbySessions.stream()
                            .sorted(java.util.Comparator.comparing(Session::getStartTime))
                            .limit(10)
                            .collect(Collectors.toList());
                    if (nearbySessions.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","暂无场次信息")));
                    // 补充电影和影院名称
                    Map<Long, String> movieTitleMap = new HashMap<>();
                    for (Movie m : mm.findByStatusOrderByRating("showing", 9999)) movieTitleMap.put(m.getId(), m.getTitle());
                    Map<Long, Cinema> nearbyCinemaMap = new HashMap<>();
                    for (Cinema c : cm.findAllNoLimit()) nearbyCinemaMap.put(c.getId(), c);
                    Map<Long, String> hallNameMap = new HashMap<>();
                    for (Session s : nearbySessions) {
                        if (s.getHallId() != null && !hallNameMap.containsKey(s.getHallId())) {
                            Hall h = hm.findById(s.getHallId());
                            hallNameMap.put(s.getHallId(), h != null ? h.getName() : "");
                        }
                    }
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (Session s : nearbySessions) {
                        Map<String, Object> i = new HashMap<>();
                        i.put("session_id", s.getId());
                        i.put("movie_id", s.getMovieId());
                        i.put("movie_title", movieTitleMap.getOrDefault(s.getMovieId(), "未知电影"));
                        Cinema cin = nearbyCinemaMap.get(s.getCinemaId());
                        i.put("cinema_id", s.getCinemaId());
                        i.put("cinema_name", cin != null ? cin.getName() : "");
                        i.put("hall_name", hallNameMap.getOrDefault(s.getHallId(), ""));
                        i.put("start_time", s.getStartTime() != null ? s.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "");
                        i.put("price", s.getPrice());
                        i.put("status", s.getStatus());
                        items.add(i);
                    }
                    return MAPPER.writeValueAsString(items);
                }

                // ===== 有电影名的正常场次查询 =====

                List<Movie> mm2 = mm.searchByKeyword(movieName, "showing", 10);
                if (mm2.isEmpty()) mm2 = mm.searchByKeyword(movieName, "coming", 10);
                if (mm2.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到电影: "+movieName)));
                if (mm2.size() > 1) { List<String> t = mm2.stream().map(Movie::getTitle).collect(Collectors.toList()); return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation",true,"type","movie","options",t,"message","找到多部电影："+String.join(" / ",t)))); }
                Movie movie = mm2.get(0);

                // 影院过滤逻辑
                List<Session> sessions;
                if (cinemaName != null && !cinemaName.isBlank()) {
                    List<Cinema> cm2 = cm.searchByName(cinemaName, 5);
                    if (cm2.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到影院: "+cinemaName)));
                    if (cm2.size() > 1) { List<String> n = cm2.stream().map(Cinema::getName).collect(Collectors.toList()); return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation",true,"type","cinema","options",n,"message","找到多家影院："+String.join(" / ",n)))); }
                    sessions = sm.findByMovieAndCinema(movie.getId(), cm2.get(0).getId());
                    sessions = sessions.stream()
                            .filter(s -> s.getStartTime() != null && !s.getStartTime().isBefore(now))
                            .collect(Collectors.toList());
                } else if (hasLocation) {
                    List<Cinema> allCinemas = cm.findAllNoLimit();
                    Set<Long> nearbyCinemaIds = allCinemas.stream()
                            .filter(c -> c.getLongitude() != null && c.getLatitude() != null)
                            .filter(c -> haversineKm(userLng, userLat,
                                    c.getLongitude().doubleValue(), c.getLatitude().doubleValue()) <= 5.0)
                            .map(Cinema::getId)
                            .collect(Collectors.toSet());
                    if (nearbyCinemaIds.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","您附近5公里内暂无影院有排片")));
                    sessions = sm.findByMovieIdAndDate(movie.getId(), now, farFuture);
                    sessions = sessions.stream()
                            .filter(s -> nearbyCinemaIds.contains(s.getCinemaId()))
                            .collect(Collectors.toList());
                } else {
                    sessions = sm.findByMovieIdAndDate(movie.getId(), now, farFuture);
                }

                if (sessions.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到当天及以后的场次")));

                // 按指定日期过滤场次
                if (filterDate != null) {
                    java.time.LocalDate fDate = filterDate;
                    sessions = sessions.stream()
                            .filter(s -> s.getStartTime() != null
                                    && s.getStartTime().toLocalDate().equals(fDate))
                            .collect(Collectors.toList());
                    if (sessions.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","该日期没有找到排片")));
                }

                // sort_by=price 时按票价升序排序
                if ("price".equals(sortBy)) {
                    sessions = sessions.stream()
                            .sorted(java.util.Comparator.comparing(Session::getPrice))
                            .collect(Collectors.toList());
                }

                // time_preference 有值时：过滤前后2小时场次 + 按时间差排序 + 高亮最接近的
                LocalDateTime targetTime = null;
                if (targetHour != null && targetMinute != null) {
                    // 构建目标时间：用 filterDate 或今天，拼上 hour:minute
                    java.time.LocalDate baseDate = filterDate != null ? filterDate : java.time.LocalDate.now();
                    targetTime = LocalDateTime.of(baseDate, java.time.LocalTime.of(targetHour, targetMinute));

                    final LocalDateTime tgt = targetTime;
                    // 过滤：场次在目标时间前后2小时内
                    sessions = sessions.stream()
                            .filter(s -> s.getStartTime() != null)
                            .filter(s -> {
                                long diffMin = Math.abs(java.time.Duration.between(s.getStartTime(), tgt).toMinutes());
                                return diffMin <= 120; // 前后2小时内
                            })
                            .collect(Collectors.toList());

                    if (sessions.isEmpty()) {
                        return MAPPER.writeValueAsString(List.of(Map.of("error","该时间段前后2小时内未找到合适场次")));
                    }

                    // 按时间差绝对值升序排序
                    sessions = sessions.stream()
                            .sorted(java.util.Comparator.comparingLong(
                                    s -> Math.abs(java.time.Duration.between(s.getStartTime(), tgt).toMinutes())))
                            .collect(Collectors.toList());
                }

                List<Map<String,Object>> items = new ArrayList<>();
                Map<Long, String> hallNameMap2 = new HashMap<>();
                for (Session s : sessions) {
                    if (s.getHallId() != null && !hallNameMap2.containsKey(s.getHallId())) {
                        Hall h = hm.findById(s.getHallId());
                        hallNameMap2.put(s.getHallId(), h != null ? h.getName() : "");
                    }
                }
                for (Session s : sessions) {
                    var i = new LinkedHashMap<String,Object>();
                    i.put("id", s.getId());
                    i.put("start_time", s.getStartTime() != null ? s.getStartTime().toString() : "");
                    i.put("end_time", s.getEndTime() != null ? s.getEndTime().toString() : "");
                    i.put("price", s.getPrice());
                    i.put("status", s.getStatus());
                    i.put("movie_title", movie.getTitle());
                    i.put("hall_name", hallNameMap2.getOrDefault(s.getHallId(), ""));
                    Cinema cin = cinemaMap.get(s.getCinemaId());
                    i.put("cinema_id", s.getCinemaId());
                    i.put("cinema_name", cin != null ? cin.getName() : "");
                    // 高亮最接近的场次（第一个，已按时间差排序）
                    if (targetTime != null) {
                        i.put("is_highlight", false); // 先都设false
                    }
                    items.add(i);
                }
                // 标记第一条为高亮
                if (!items.isEmpty() && targetTime != null) {
                    ((Map<String,Object>) items.get(0)).put("is_highlight", true);
                }
                return MAPPER.writeValueAsString(items);
            } catch (Exception e) { return "[{\"error\":\"查询场次失败\"}]"; }
        });
    }

    @Bean public ToolCallback getUserOrders(OrderService os) {
        return cb("get_user_orders", "查询当前登录用户的订单列表。user_id 会被系统自动注入。退票场景必须传 status_filter=paid 只查已支付订单。",
                "{\"type\":\"object\",\"properties\":{\"user_id\":{\"type\":\"integer\",\"description\":\"用户ID（系统自动注入，无需关心）\"},\"status_filter\":{\"type\":\"string\",\"description\":\"状态筛选: paid(已支付)/pending(待支付)/cancelled(已取消)/refunded(已退款)。退票场景必须传paid\"}},\"required\":[\"status_filter\"]}",
                input -> {
            try { Map<String,Object> a = MAPPER.readValue(input, Map.class); Object uidObj = a.get("user_id");
                if (uidObj == null) { log.warn("[get_user_orders] user_id 缺失, input={}", input); return "[{\"error\":\"用户ID缺失，无法查询订单\"}]"; }
                Long uid = ((Number) uidObj).longValue(); String sf = (String)a.get("status_filter");
                log.info("[get_user_orders] 调用 listOrders, uid={}, statusFilter={}", uid, sf);
                return MAPPER.writeValueAsString(os.listOrders(uid, sf)); }
            catch (Exception e) { log.error("[get_user_orders] 查询订单异常", e); return "[{\"error\":\"查询订单失败\"}]"; }
        });
    }

    // refund_order — 对话中退票，LLM展示订单后用户确认，再调用此工具执行退款。
    @Bean public ToolCallback refundOrder(OrderService os, SessionMapper sm) {
        return cb("refund_order", "退票退款。用户确认退票后调用此工具执行退款操作。order_id 和 user_id 会被系统自动注入。",
                "{\"type\":\"object\",\"properties\":{\"order_id\":{\"type\":\"integer\",\"description\":\"要退款的订单ID\"},\"user_id\":{\"type\":\"integer\",\"description\":\"用户ID（系统自动注入，无需关心）\"}},\"required\":[\"order_id\"]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                Object oidObj = a.get("order_id");
                Object uidObj = a.get("user_id");
                if (oidObj == null) return "{\"error\":\"缺少订单ID\"}";
                if (uidObj == null) return "{\"error\":\"用户ID缺失\"}";
                Long orderId = ((Number) oidObj).longValue();
                Long userId = ((Number) uidObj).longValue();
                // 先查订单确认状态
                Order order = os.getOrder(orderId, userId);
                if (order == null) return "{\"error\":\"订单不存在\"}";
                if (!"paid".equals(order.getStatus())) return "{\"error\":\"只能退已支付订单\"}";
                // 校验退票时间：开场前2小时才可退票
                Session session = sm.findById(order.getSessionId());
                if (session != null && session.getStartTime() != null) {
                    java.time.LocalDateTime now = java.time.LocalDateTime.now();
                    java.time.LocalDateTime deadline = session.getStartTime().minusHours(2);
                    if (now.isAfter(deadline)) {
                        return "{\"error\":\"场次开场前2小时内不可退票\"}";
                    }
                }
                // 执行退款（更新订单状态 + 释放座位）
                os.refundOrder(orderId, userId);
                log.info("[refund_order] 退票成功: orderId={}, userId={}", orderId, userId);
                var r = new LinkedHashMap<String,Object>();
                r.put("success", true);
                r.put("message", "退款成功，退款将原路返回");
                r.put("order_id", orderId);
                r.put("order_no", order.getOrderNo());
                r.put("refund_amount", order.getTotalAmount());
                return MAPPER.writeValueAsString(r);
            } catch (Exception e) {
                log.error("[refund_order] 退票异常", e);
                return "{\"error\":\"退票失败: " + e.getMessage() + "\"}";
            }
        });
    }

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