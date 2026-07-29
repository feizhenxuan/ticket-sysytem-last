package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.MovieService;
import com.alipay.ticketbacked.core.model.dto.MovieDTO;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;

/**
 * 票房排行接口 — 对应 Python /api/box-office
 * 基于 rating + 随机种子生成稳定的 mock 票房数字。
 */
@RestController
@RequestMapping("/api/box-office")
public class BoxOfficeController {

    private final MovieService movieService;

    public BoxOfficeController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public Map<String, Object> boxOfficeRanking(
            @RequestParam(defaultValue = "showing") String status,
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 50);
        List<MovieDTO> movies = movieService.listMovies("rating", status, limit);

        List<Map<String, Object>> items = new ArrayList<>();
        double totalBoxOffice = 0;

        for (MovieDTO m : movies) {
            double amount = mockBoxOffice(m.getId(), m.getRating().doubleValue(), status);
            totalBoxOffice += amount;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("rank", 0);
            item.put("id", m.getId());
            item.put("title", m.getTitle());
            item.put("rating", m.getRating());
            item.put("poster_url", m.getPosterUrl());
            item.put("release_date", m.getReleaseDate() != null ? m.getReleaseDate().toString() : null);
            item.put("box_office", BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP));
            item.put("unit", "万");
            items.add(item);
        }

        // 按票房降序
        items.sort((a, b) -> ((BigDecimal) b.get("box_office")).compareTo((BigDecimal) a.get("box_office")));
        for (int i = 0; i < items.size(); i++) {
            items.get(i).put("rank", i + 1);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        result.put("total_box_office", "showing".equals(status) ? BigDecimal.valueOf(totalBoxOffice).setScale(2, RoundingMode.HALF_UP) : null);
        result.put("date", LocalDate.now().toString());
        result.put("status", status);
        return result;
    }

    /** 基于 movie_id 做种子的伪随机票房，保证幂等 */
    private double mockBoxOffice(Long movieId, double rating, String status) {
        try {
            String seedKey = movieId + "-" + status;
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(seedKey.getBytes(StandardCharsets.UTF_8));
            long seed = 0;
            for (byte b : hash) seed = (seed << 8) | (b & 0xFF);
            Random rng = new Random(seed);
            if ("showing".equals(status)) {
                return BigDecimal.valueOf(rating * 300 + (50 + rng.nextDouble() * 750))
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            } else {
                return BigDecimal.valueOf(1 + rng.nextDouble() * 49 + rating * 2)
                        .setScale(2, RoundingMode.HALF_UP).doubleValue();
            }
        } catch (Exception e) {
            return 100.0;
        }
    }
}