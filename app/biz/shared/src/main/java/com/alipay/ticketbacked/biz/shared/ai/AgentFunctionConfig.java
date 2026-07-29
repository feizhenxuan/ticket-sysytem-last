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

    @Bean public ToolCallback searchSessions(MovieMapper mm, CinemaMapper cm, SessionMapper sm) {
        return cb("search_sessions", "查询某电影的排片场次，电影名和影院名支持模糊匹配。",
                "{\"type\":\"object\",\"properties\":{\"movie_name\":{\"type\":\"string\",\"description\":\"电影名称\"},\"cinema_name\":{\"type\":\"string\",\"description\":\"影院名称(可选)\"}},\"required\":[\"movie_name\"]}",
                input -> {
            try {
                Map<String,Object> a = MAPPER.readValue(input, Map.class);
                String movieName = (String) a.get("movie_name"), cinemaName = (String) a.get("cinema_name");
                List<Movie> mm2 = mm.searchByKeyword(movieName, "showing", 10);
                if (mm2.isEmpty()) mm2 = mm.searchByKeyword(movieName, "coming", 10);
                if (mm2.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到电影: "+movieName)));
                if (mm2.size() > 1) { List<String> t = mm2.stream().map(Movie::getTitle).collect(Collectors.toList()); return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation",true,"type","movie","options",t,"message","找到多部电影："+String.join(" / ",t)))); }
                Movie movie = mm2.get(0);
                List<Session> sessions;
                if (cinemaName != null && !cinemaName.isBlank()) {
                    List<Cinema> cm2 = cm.searchByName(cinemaName, 5);
                    if (cm2.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到影院: "+cinemaName)));
                    if (cm2.size() > 1) { List<String> n = cm2.stream().map(Cinema::getName).collect(Collectors.toList()); return MAPPER.writeValueAsString(List.of(Map.of("need_confirmation",true,"type","cinema","options",n,"message","找到多家影院："+String.join(" / ",n)))); }
                    sessions = sm.findByMovieAndCinema(movie.getId(), cm2.get(0).getId());
                } else sessions = sm.findByMovieId(movie.getId());
                if (sessions.isEmpty()) return MAPPER.writeValueAsString(List.of(Map.of("error","未找到场次")));
                List<Map<String,Object>> items = new ArrayList<>();
                for (Session s : sessions) { var i = new LinkedHashMap<String,Object>(); i.put("id",s.getId()); i.put("start_time",s.getStartTime()!=null?s.getStartTime().toString():""); i.put("end_time",s.getEndTime()!=null?s.getEndTime().toString():""); i.put("price",s.getPrice()); i.put("status",s.getStatus()); i.put("movie_title",movie.getTitle()); items.add(i); }
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