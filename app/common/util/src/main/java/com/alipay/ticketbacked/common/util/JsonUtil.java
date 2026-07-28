package com.alipay.ticketbacked.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON 工具 — 封装 Jackson，替代 Python json.dumps/loads
 */
@Component
public class JsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public <T> T fromJson(String json, Class<T> clazz) {
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> toMap(String json) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    public List<Object> toList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<List<Object>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}