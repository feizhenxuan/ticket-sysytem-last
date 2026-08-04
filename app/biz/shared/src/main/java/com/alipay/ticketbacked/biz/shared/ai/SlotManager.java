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

    // 影院名：xx影院/影城/电影院 — 先匹配关键词前面的1-10个字符，再清洗前缀
    private static final Pattern CINEMA_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5a-zA-Z0-9]{2,10})(影院|影城|电影院|国际影城|环球影城)"
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

        // 1. 电影名 — 优先匹配书名号
        Matcher movieMatcher = MOVIE_PATTERN.matcher(text);
        if (movieMatcher.find()) {
            slots.put("movie_name", movieMatcher.group(1).trim());
        } else {
            // 无书名号时尝试从"电影票"/"电影"前提取电影名
            String movieCandidate = extractMovieBeforeKeyword(text);
            if (movieCandidate != null) {
                slots.put("movie_name", movieCandidate);
            }
        }

        // 2. 影院名
        Matcher cinemaMatcher = CINEMA_PATTERN.matcher(text);
        if (cinemaMatcher.find()) {
            String namePart = cinemaMatcher.group(1);
            String suffix = cinemaMatcher.group(2);
            // 清洗前缀词：反复去掉开头的常见前缀字/词，直到剩下核心名称
            String cleaned = namePart;
            boolean changed = true;
            while (changed) {
                changed = false;
                String[] prefixes = {"帮我在", "帮我", "我想去", "我想", "附近", "有什么", "哪里有", "哪有", "什么",
                        "约在", "在", "去", "的", "约", "帮", "想", "附近"};
                for (String prefix : prefixes) {
                    if (cleaned.startsWith(prefix) && cleaned.length() > prefix.length() + 1) {
                        cleaned = cleaned.substring(prefix.length());
                        changed = true;
                        break;
                    }
                }
            }
            // 去掉尾部"的"
            while (cleaned.endsWith("的") && cleaned.length() > 2) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            // 如果清洗后太短（<2字符），回退使用原始匹配
            if (cleaned.length() < 2) {
                cleaned = namePart;
            }
            // 过滤纯疑问词（如"有什么影院"中的"有什么"）
            if (!cleaned.matches(".*(什么|哪|有).*")) {
                slots.put("cinema_name", cleaned + suffix);
            }
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
            try {
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
            } catch (java.time.DateTimeException | NumberFormatException e) {
                // 非法日期（如"13月40号"），跳过该槽位，不影响其他抽取
            }
        }

        return slots;
    }

    /**
     * 从"电影票"/"电影"关键词前提取电影名（无书名号场景）。
     * 策略：找到关键词位置，往前取最多10个中文字符，然后双向清洗日期/时间/数量/方位等非电影名词汇。
     */
    private String extractMovieBeforeKeyword(String text) {
        for (String keyword : new String[]{"电影票", "电影"}) {
            int idx = text.indexOf(keyword);
            if (idx < 0) continue;

            // 往前取最多10个字符
            int start = Math.max(0, idx - 10);
            String before = text.substring(start, idx);
            // 只保留中文字符
            StringBuilder chineseOnly = new StringBuilder();
            for (char c : before.toCharArray()) {
                if (c >= '\u4e00' && c <= '\u9fa5') {
                    chineseOnly.append(c);
                }
            }
            if (chineseOnly.length() < 2) continue;

            // 双向清洗非电影名词汇
            String cleaned = chineseOnly.toString();
            String[] removeWords = {
                "明天", "今天", "后天", "大后天", "下午", "上午", "晚上", "中午", "左右",
                "附近", "万达", "影院", "影城", "帮我", "帮我买", "帮我订", "帮我约",
                "订", "买", "约", "在", "去", "想", "的", "一张", "两张", "三张",
                "两", "三", "一", "张", "个", "点", "号", "日", "月",
                "没有", "有没", "看", "附近", "午", "分"
            };
            boolean changed = true;
            while (changed) {
                changed = false;
                // 按长度降序排列，优先匹配长词
                java.util.Arrays.sort(removeWords, (a, b) -> b.length() - a.length());
                for (String w : removeWords) {
                    if (cleaned.startsWith(w) && cleaned.length() > w.length() + 1) {
                        cleaned = cleaned.substring(w.length());
                        changed = true;
                        break;
                    }
                    if (cleaned.endsWith(w) && cleaned.length() > w.length() + 1) {
                        cleaned = cleaned.substring(0, cleaned.length() - w.length());
                        changed = true;
                        break;
                    }
                }
            }

            if (cleaned.length() >= 2) {
                return cleaned;
            }
        }
        return null;
    }
}