package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.core.model.ChatSession;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 槽位管理器 — 服务端正则优先抽取，不依赖 LLM 自觉调 save_slots。
 * 抽取结果由 ChatAgentService 合并到 session.slots 并注入 prompt。
 */
@Component
public class SlotManager {

    // 电影名：《xxx》 或 「xxx」
    private static final Pattern MOVIE_PATTERN = Pattern.compile(
            "[《【「](.+?)[》】」]"
    );

    // 购票数量：N张/票/个人
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "(\\d+)\\s*(?:张|票|个人|个人)"
    );

    // 影院名：xx影院/影城/电影院
    private static final Pattern CINEMA_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5a-zA-Z0-9]{2,}(?:影院|影城|电影院|国际影城|环球影城))"
    );

    // 时间表达：今天/明天/后天/大后天 + 上午/下午/晚上 + 时间点
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(今天|明天|后天|大后天|周[一二三四五六日天]|本周[一二三四五六日天]|下周[一二三四五六日天])" +
            "(?:\\s*(上午|下午|晚上|中午|Morning|afternoon|evening))?" +
            "(?:\\s*(\\d{1,2})[:点](\\d{2})?)?"
    );

    // 单独的时间词
    private static final Pattern TIME_ONLY_PATTERN = Pattern.compile(
            "^(今天|明天|后天|大后天)$"
    );

    /**
     * 从用户消息中抽取槽位。
     * 返回的 Map 只包含本轮新抽取到的槽位，不包含历史。
     */
    public Map<String, Object> extract(String text, ChatSession session) {
        if (text == null || text.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, Object> slots = new LinkedHashMap<>();

        // 1. 电影名
        Matcher movieMatcher = MOVIE_PATTERN.matcher(text);
        if (movieMatcher.find()) {
            slots.put("movie_name", movieMatcher.group(1).trim());
        }

        // 2. 影院名
        Matcher cinemaMatcher = CINEMA_PATTERN.matcher(text);
        if (cinemaMatcher.find()) {
            slots.put("cinema_name", cinemaMatcher.group(1).trim());
        }

        // 3. 购票数量
        Matcher countMatcher = COUNT_PATTERN.matcher(text);
        if (countMatcher.find()) {
            try {
                slots.put("ticket_count", countMatcher.group(1));
            } catch (NumberFormatException ignored) {
                // This catch statement is intentionally empty
            }
        }

        // 4. 时间表达
        Matcher timeMatcher = TIME_PATTERN.matcher(text);
        if (timeMatcher.find()) {
            StringBuilder timeExpr = new StringBuilder(timeMatcher.group(1));
            if (timeMatcher.group(2) != null) {
                timeExpr.append(" ").append(timeMatcher.group(2));
            }
            if (timeMatcher.group(3) != null) {
                timeExpr.append(" ").append(timeMatcher.group(3));
                if (timeMatcher.group(4) != null) {
                    timeExpr.append(":").append(timeMatcher.group(4));
                } else {
                    timeExpr.append("点");
                }
            }
            slots.put("time_expression", timeExpr.toString());
        } else if (TIME_ONLY_PATTERN.matcher(text.trim()).find()) {
            slots.put("time_expression", text.trim());
        }

        return slots;
    }
}