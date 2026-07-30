package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.core.model.ChatSession;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Year;
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

    // 绝对日期：N月N号/N月N日
    private static final Pattern ABSOLUTE_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[号日]"
    );

    // 绝对日期可带时间段和时分
    private static final Pattern ABSOLUTE_DATE_FULL_PATTERN = Pattern.compile(
            "(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[号日]" +
            "(?:\\s*(上午|下午|晚上|中午))?" +
            "(?:\\s*(\\d{1,2})[:点](\\d{2})?)?"
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

        // 4. 时间表达 — 优先匹配相对日期（今天/明天等），再尝试绝对日期（7月15号）
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

        // 4b. 绝对日期：7月15号 / 7月15号晚上8点
        Matcher absDateMatcher = ABSOLUTE_DATE_FULL_PATTERN.matcher(text);
        if (absDateMatcher.find() && !slots.containsKey("time_expression")) {
            int month = Integer.parseInt(absDateMatcher.group(1));
            int day = Integer.parseInt(absDateMatcher.group(2));
            int year = Year.now().getValue();

            // 如果月份已过，默认是明年
            LocalDate candidate = LocalDate.of(year, month, day);
            if (candidate.isBefore(LocalDate.now())) {
                candidate = LocalDate.of(year + 1, month, day);
            }

            StringBuilder timeExpr = new StringBuilder(candidate.toString()); // yyyy-MM-dd
            if (absDateMatcher.group(3) != null) {
                timeExpr.append(" ").append(absDateMatcher.group(3));
            }
            if (absDateMatcher.group(4) != null) {
                timeExpr.append(" ").append(absDateMatcher.group(4));
                if (absDateMatcher.group(5) != null) {
                    timeExpr.append(":").append(absDateMatcher.group(5));
                } else {
                    timeExpr.append("点");
                }
            }
            slots.put("time_expression", timeExpr.toString());
        }

        return slots;
    }
}