package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.service.CinemaService;
import com.alipay.ticketbacked.common.service.integration.AmapClient;
import com.alipay.ticketbacked.core.model.dto.CinemaDTO;
import com.alipay.ticketbacked.core.model.BizException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 影院接口 — 对应 Python /api/cinemas
 */
@RestController
@RequestMapping("/api/cinemas")
public class CinemaController {

    private final CinemaService cinemaService;
    private final AmapClient amapClient;

    public CinemaController(CinemaService cinemaService, AmapClient amapClient) {
        this.cinemaService = cinemaService;
        this.amapClient = amapClient;
    }

    @GetMapping
    public Map<String, Object> listCinemas(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "50") int limit) {
        limit = Math.max(1, Math.min(limit, 100));

        // 有坐标时，先逆地理获取城市名，再按城市查数据库影院
        if (lat != null && lng != null) {
            String resolvedCity = city;
            try {
                Map<String, Object> regeo = amapClient.reverseGeocode(lng, lat);
                if (regeo != null && regeo.get("city") != null) {
                    resolvedCity = (String) regeo.get("city");
                }
            } catch (Exception ignored) {}

            // 去掉"市"后缀，用关键词模糊匹配（北京 vs 北京市）
            String keyword = resolvedCity != null ? resolvedCity.replace("市", "").replace("省", "") : null;
            List<CinemaDTO> items = cinemaService.listCinemasNearby(keyword, lat, lng, limit);
            Map<String, Object> result = new HashMap<>();
            result.put("items", items);
            result.put("total", items.size());
            result.put("distance_provider", "haversine");
            result.put("resolved_city", resolvedCity);
            return result;
        }

        // 无坐标时按城市过滤
        List<CinemaDTO> items = cinemaService.listCinemasNearby(city, null, null, limit);
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", items.size());
        result.put("distance_provider", null);
        return result;
    }

    @GetMapping("/{id}")
    public CinemaDTO getCinema(@PathVariable Long id) {
        CinemaDTO dto = cinemaService.getCinema(id);
        if (dto == null) throw BizException.notFound("影院不存在");
        return dto;
    }

    /** 地址转经纬度 — 对应 Python /api/cinemas/geocode */
    @GetMapping("/geocode")
    public Map<String, Object> geocode(@RequestParam String address,
                                       @RequestParam(defaultValue = "") String city) {
        Map<String, Object> result = amapClient.geocode(address, city);
        if (result == null) throw BizException.notFound("未找到该地址");
        return result;
    }

    /** 经纬度转地址 — 对应 Python /api/cinemas/regeo */
    @GetMapping("/regeo")
    public Map<String, Object> regeo(@RequestParam double lng, @RequestParam double lat) {
        Map<String, Object> result = amapClient.reverseGeocode(lng, lat);
        if (result == null) return Map.of("city", "", "province", "", "formatted", "", "lng", lng, "lat", lat);
        return result;
    }
}