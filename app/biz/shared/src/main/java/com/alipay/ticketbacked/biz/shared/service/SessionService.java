package com.alipay.ticketbacked.biz.shared.service;

import com.alipay.ticketbacked.common.dal.mapper.*;
import com.alipay.ticketbacked.core.model.*;
import com.alipay.ticketbacked.core.model.dto.SessionDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 场次服务 — 对应 Python api/sessions.py
 */
@Service
public class SessionService {

    private final SessionMapper sessionMapper;
    private final CinemaMapper cinemaMapper;
    private final MovieMapper movieMapper;
    private final HallMapper hallMapper;
    private final SessionSeatMapper sessionSeatMapper;

    public SessionService(SessionMapper sessionMapper, CinemaMapper cinemaMapper,
                          MovieMapper movieMapper, HallMapper hallMapper,
                          SessionSeatMapper sessionSeatMapper) {
        this.sessionMapper = sessionMapper;
        this.cinemaMapper = cinemaMapper;
        this.movieMapper = movieMapper;
        this.hallMapper = hallMapper;
        this.sessionSeatMapper = sessionSeatMapper;
    }

    /** 场次列表查询 */
    public List<SessionDTO> listSessions(Long movieId, Long cinemaId, String date) {
        List<Session> sessions;
        if (movieId != null && cinemaId != null) {
            sessions = sessionMapper.findByMovieAndCinema(movieId, cinemaId);
        } else if (movieId != null) {
            sessions = sessionMapper.findByMovieId(movieId);
        } else if (cinemaId != null) {
            sessions = sessionMapper.findByCinemaId(cinemaId);
        } else {
            sessions = sessionMapper.findAllAvailable();
        }

        // 按日期筛选
        if (date != null && !date.isBlank()) {
            LocalDateTime start = java.time.LocalDate.parse(date).atStartOfDay();
            LocalDateTime end = start.plusDays(1);
            sessions = sessions.stream()
                    .filter(s -> !s.getStartTime().isBefore(start) && s.getStartTime().isBefore(end))
                    .collect(Collectors.toList());
        }

        if (sessions.isEmpty()) return Collections.emptyList();

        // 预加载关联数据（避免 N+1）
        Set<Long> cinemaIds = sessions.stream().map(Session::getCinemaId).collect(Collectors.toSet());
        Set<Long> movieIds = sessions.stream().map(Session::getMovieId).collect(Collectors.toSet());
        Set<Long> hallIds = sessions.stream().map(Session::getHallId).collect(Collectors.toSet());

        Map<Long, Cinema> cinemaMap = cinemaIds.isEmpty() ? Map.of()
                : cinemaMapper.findAll(9999).stream().collect(Collectors.toMap(Cinema::getId, c -> c));
        Map<Long, Movie> movieMap = movieIds.isEmpty() ? Map.of()
                : movieMapper.findByStatusOrderByRating("showing", 9999).stream()
                    .collect(Collectors.toMap(Movie::getId, m -> m, (a, b) -> a));
        // 如果电影不在 "showing"，也查 coming
        for (Long mid : movieIds) {
            if (!movieMap.containsKey(mid)) {
                Movie m = movieMapper.findById(mid);
                if (m != null) movieMap.put(mid, m);
            }
        }
        Map<Long, Hall> hallMap = hallMapper.findByIds(new ArrayList<>(hallIds))
                .stream().collect(Collectors.toMap(Hall::getId, h -> h));

        return sessions.stream().map(s -> {
            SessionDTO dto = new SessionDTO();
            dto.setId(s.getId());
            dto.setMovieId(s.getMovieId());
            dto.setCinemaId(s.getCinemaId());
            dto.setHallId(s.getHallId());
            dto.setStartTime(s.getStartTime());
            dto.setEndTime(s.getEndTime());
            dto.setPrice(s.getPrice());
            dto.setStatus(s.getStatus());
            Cinema c = cinemaMap.get(s.getCinemaId());
            dto.setCinemaName(c != null ? c.getName() : "");
            dto.setCinemaAddress(c != null ? c.getAddress() : "");
            dto.setCinemaCity(c != null ? c.getCity() : "");
            Movie m = movieMap.get(s.getMovieId());
            dto.setMovieTitle(m != null ? m.getTitle() : "");
            dto.setPosterUrl(m != null ? m.getPosterUrl() : null);
            Hall h = hallMap.get(s.getHallId());
            dto.setHallName(h != null ? h.getName() : "");
            dto.setHallType(h != null ? h.getHallType() : "");
            return dto;
        }).collect(Collectors.toList());
    }

    /** 管理端场次列表 — 返回所有状态的场次（包括 closed） */
    public List<SessionDTO> listAllSessionsForAdmin(Long movieId, Long cinemaId) {
        List<Session> sessions;
        if (movieId != null && cinemaId != null) {
            sessions = sessionMapper.findByMovieAndCinema(movieId, cinemaId);
        } else if (movieId != null) {
            sessions = sessionMapper.findByMovieId(movieId);
        } else if (cinemaId != null) {
            sessions = sessionMapper.findByCinemaId(cinemaId);
        } else {
            sessions = sessionMapper.findAll();
        }

        if (sessions.isEmpty()) return Collections.emptyList();

        Set<Long> cinemaIds = sessions.stream().map(Session::getCinemaId).collect(Collectors.toSet());
        Set<Long> movieIds = sessions.stream().map(Session::getMovieId).collect(Collectors.toSet());
        Set<Long> hallIds = sessions.stream().map(Session::getHallId).collect(Collectors.toSet());

        Map<Long, Cinema> cinemaMap = cinemaIds.isEmpty() ? Map.of()
                : cinemaMapper.findAll(9999).stream().collect(Collectors.toMap(Cinema::getId, c -> c));
        Map<Long, Movie> movieMap = movieIds.isEmpty() ? Map.of()
                : movieMapper.findAllOrderByRating(9999).stream()
                .collect(Collectors.toMap(Movie::getId, m -> m, (a, b) -> a));
        Map<Long, Hall> hallMap = hallIds.isEmpty() ? Map.of()
                : hallMapper.findByIds(new ArrayList<>(hallIds)).stream()
                .collect(Collectors.toMap(Hall::getId, h -> h));

        return sessions.stream().map(s -> {
            SessionDTO dto = new SessionDTO();
            dto.setId(s.getId());
            dto.setMovieId(s.getMovieId());
            dto.setCinemaId(s.getCinemaId());
            dto.setHallId(s.getHallId());
            dto.setStartTime(s.getStartTime());
            dto.setEndTime(s.getEndTime());
            dto.setPrice(s.getPrice());
            dto.setStatus(s.getStatus());
            Cinema c = cinemaMap.get(s.getCinemaId());
            dto.setCinemaName(c != null ? c.getName() : "");
            Movie m = movieMap.get(s.getMovieId());
            dto.setMovieTitle(m != null ? m.getTitle() : "");
            Hall h = hallMap.get(s.getHallId());
            dto.setHallName(h != null ? h.getName() : "");
            dto.setHallType(h != null ? h.getHallType() : "");
            return dto;
        }).collect(Collectors.toList());
    }

    /** 单场次详情 */
    public Map<String, Object> getSessionDetail(Long sessionId) {
        Session s = sessionMapper.findById(sessionId);
        if (s == null) return null;

        Cinema c = cinemaMapper.findById(s.getCinemaId());
        Movie m = movieMapper.findById(s.getMovieId());
        Hall h = hallMapper.findById(s.getHallId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", s.getId());
        result.put("movie_id", s.getMovieId());
        result.put("cinema_id", s.getCinemaId());
        result.put("hall_id", s.getHallId());
        result.put("start_time", s.getStartTime());
        result.put("end_time", s.getEndTime());
        result.put("price", s.getPrice());
        result.put("status", s.getStatus());
        result.put("cinema_name", c != null ? c.getName() : "");
        result.put("cinema_address", c != null ? c.getAddress() : "");
        result.put("movie_title", m != null ? m.getTitle() : "");
        result.put("poster_url", m != null ? m.getPosterUrl() : null);
        result.put("movie_genre", m != null ? m.getGenre() : null);
        result.put("movie_duration", m != null ? m.getDuration() : null);
        result.put("hall_name", h != null ? h.getName() : "");
        result.put("hall_type", h != null ? h.getHallType() : "");
        result.put("total_rows", h != null ? h.getTotalRows() : 8);
        result.put("total_cols", h != null ? h.getTotalCols() : 12);
        return result;
    }

    /** 场次座位状态 */
    public List<Map<String, Object>> getSessionSeats(Long sessionId) {
        return sessionSeatMapper.findSessionSeats(sessionId);
    }

    // ===== Admin CRUD =====
    public void createSession(Session session) { sessionMapper.insert(session); }
    public void updateSession(Session session) { sessionMapper.update(session); }
    public void deleteSession(Long id) { sessionMapper.deleteById(id); }
}