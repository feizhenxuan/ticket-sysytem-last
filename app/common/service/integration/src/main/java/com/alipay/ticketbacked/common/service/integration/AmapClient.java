package com.alipay.ticketbacked.common.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 高德地图 API 客户端 — 对应 Python utils/amap.py
 */
@Component
public class AmapClient {

    private static final Logger log = LoggerFactory.getLogger(AmapClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${app.amap.api-key}")
    private String apiKey;

    @Value("${app.amap.base-url}")
    private String baseUrl;

    private final RestClient httpClient = RestClient.create();

    /** Haversine 距离计算（米）— 对应 Python calculate_distance */
    public static int calculateDistance(double lng1, double lat1, double lng2, double lat2) {
        double R = 6371000; // 地球半径(米)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) (R * c);
    }

    /** 地址转经纬度 — 对应 Python geocode */
    public Map<String, Object> geocode(String address, String city) {
        try {
            String url = baseUrl + "/v3/geocode/geo?key=" + apiKey
                    + "&address=" + java.net.URLEncoder.encode(address, "UTF-8");
            if (city != null && !city.isBlank()) {
                url += "&city=" + java.net.URLEncoder.encode(city, "UTF-8");
            }
            // 用 java.net.URI 避免 RestClient 二次编码
            java.net.URI uri = java.net.URI.create(url);
            String json = httpClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = MAPPER.readTree(json);
            if (!"1".equals(root.path("status").asText())) return null;
            JsonNode loc = root.path("geocodes").path(0);
            if (loc.isMissingNode()) return null;
            String location = loc.path("location").asText(""); // "lng,lat"
            String[] parts = location.split(",");
            if (parts.length < 2) return null;
            Map<String, Object> result = new HashMap<>();
            result.put("formatted", loc.path("formatted_address").asText(""));
            result.put("lng", Double.parseDouble(parts[0]));
            result.put("lat", Double.parseDouble(parts[1]));
            return result;
        } catch (Exception e) {
            log.warn("高德 geocode 失败: {}", e.getMessage());
            return null;
        }
    }

    /** 经纬度转地址 — 对应 Python reverse_geocode */
    public Map<String, Object> reverseGeocode(double lng, double lat) {
        try {
            String url = baseUrl + "/v3/geocode/regeo?key=" + apiKey
                    + "&location=" + lng + "," + lat + "&extensions=base";
            java.net.URI uri = java.net.URI.create(url);
            String json = httpClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = MAPPER.readTree(json);
            if (!"1".equals(root.path("status").asText())) return null;
            JsonNode addr = root.path("regeocode");
            Map<String, Object> result = new HashMap<>();
            result.put("formatted", addr.path("formatted_address").asText(""));
            JsonNode addrComp = addr.path("addressComponent");
            // 高德对直辖市 city 返回空数组或空字符串，用 province 兜底
            String cityVal = addrComp.path("city").asText("");
            if (cityVal.isEmpty() || "[]".equals(cityVal)) {
                cityVal = addrComp.path("province").asText("");
            }
            result.put("city", cityVal);
            result.put("province", addrComp.path("province").asText(""));
            result.put("lng", lng);
            result.put("lat", lat);
            return result;
        } catch (Exception e) {
            log.warn("高德 reverse_geocode 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 搜索当前位置附近的影院（高德 POI 搜索 API）
     * 返回影院列表，每项包含 name/address/location/phone/距离
     */
    public List<Map<String, Object>> searchNearbyCinemas(double lng, double lat, int radius, int limit) {
        try {
            String keywords = java.net.URLEncoder.encode("影院|电影院|影城", "UTF-8");
            String url = baseUrl + "/v5/place/around?key=" + apiKey
                    + "&location=" + lng + "," + lat
                    + "&keywords=" + keywords
                    + "&types=080600"
                    + "&radius=" + radius
                    + "&sort=distance"
                    + "&page_size=" + Math.min(limit, 25)
                    + "&page=1"
                    + "&extensions=base";
            java.net.URI uri = java.net.URI.create(url);
            String json = httpClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = MAPPER.readTree(json);
            if (!"1".equals(root.path("status").asText())) {
                log.warn("高德 POI 搜索失败: status={}, info={}", root.path("status").asText(), root.path("info").asText());
                return new ArrayList<>();
            }
            JsonNode pois = root.path("pois");
            List<Map<String, Object>> results = new ArrayList<>();
            if (pois.isArray()) {
                for (JsonNode poi : pois) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", poi.path("name").asText(""));
                    item.put("address", poi.path("address").asText(""));
                    String location = poi.path("location").asText("");
                    if (!location.isEmpty() && location.contains(",")) {
                        String[] parts = location.split(",");
                        item.put("longitude", Double.parseDouble(parts[0]));
                        item.put("latitude", Double.parseDouble(parts[1]));
                    }
                    // 距离（米）
                    String distStr = poi.path("distance").asText("");
                    if (!distStr.isEmpty()) {
                        item.put("distance", Integer.parseInt(distStr));
                    }
                    item.put("phone", poi.path("tel").asText(""));
                    item.put("city", poi.path("cityname").asText(""));
                    // 生成伪 id（用高德.poi_id 或 hash）
                    String poiId = poi.path("id").asText("");
                    item.put("id", poiId.hashCode() & 0x7FFFFFFF);
                    results.add(item);
                }
            }
            log.info("高德 POI 搜索成功: 附近 {} 家影院", results.size());
            return results;
        } catch (Exception e) {
            log.warn("高德 POI 搜索异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
}