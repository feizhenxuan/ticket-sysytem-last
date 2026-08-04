package com.alipay.ticketbacked.biz.shared.ai;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 意图识别器 — 规则优先，覆盖不到的走 CHAT 兜底让 LLM 自行判断。
 * 后续可扩展小模型分类作为 fallback。
 */
@Component
public class IntentDetector {

    public enum Intent {
        BOOK_TICKET,    // 购票
        REFUND_TICKET,  // 退票
        QUERY_MOVIE,    // 查电影
        QUERY_CINEMA,  // 查影院
        QUERY_ORDER,    // 查订单
        REJECT          // 闲聊/无关
    }

    // 购票关键词 — 不含独立"票"字，避免"我的票""取票码"被误判为购票
    // 包含"去...影院/影城"等表达购票意图的模式
    private static final Pattern BOOK_PATTERN = Pattern.compile(
            "(买票|订票|购票|订.*票|购.*票|买.*票|想看|要看|看一下.*电影|《.+》.*(?:买|订|看)|(?:买|订).+《|(?:想去|要去|去).+(?:影院|影城|电影院))"
    );

    // 退票关键词
    private static final Pattern REFUND_PATTERN = Pattern.compile(
            "(退票|退款|退掉|退了|取消订单|取消第.*单|取消.*笔|不看了|不要了)"
    );

    // 查电影关键词
    private static final Pattern MOVIE_PATTERN = Pattern.compile(
            "(什么电影|有什么电影|电影推荐|推荐.*电影|评分.*高|最近.*上映|热映|正在.*映|好看.*电影|价格.*低|便宜.*电影|便宜.*票|低价.*电影|最便宜)"
    );

    // 查影院关键词
    private static final Pattern CINEMA_PATTERN = Pattern.compile(
            "(什么影院|有什么影院|附近.*影院|影院.*推荐|电影院|哪里.*看电影)"
    );

    // 查订单关键词 — 优先级提到 BOOK 之前
    private static final Pattern ORDER_PATTERN = Pattern.compile(
            "(我的.*订单|订单.*列表|待支付|已购买|已支付.*订单|我的.*票|取票码|我的票)"
    );

    // 打招呼
    private static final Pattern GREETING_PATTERN = Pattern.compile(
            "(你好|hi|hello|嗨|在吗|在不在|您好)"
    );

    public Intent detect(String text) {
        if (text == null || text.isBlank()) {
            return Intent.REJECT;
        }

        String lower = text.trim();

        // 打招呼最优先
        if (lower.length() <= 10 && GREETING_PATTERN.matcher(lower).find()) {
            return Intent.REJECT;
        }

        // 退票优先匹配
        if (REFUND_PATTERN.matcher(lower).find()) {
            return Intent.REFUND_TICKET;
        }

        // 查订单在购票之前（避免"我的票"被误判为购票）
        if (ORDER_PATTERN.matcher(lower).find()) {
            return Intent.QUERY_ORDER;
        }

        if (MOVIE_PATTERN.matcher(lower).find()) {
            return Intent.QUERY_MOVIE;
        }

        // 购票意图需要先于 CINEMA 检测，避免"附近影院订票"被误判为纯查影院
        // 但只有 BOOK_PATTERN 真正匹配时才优先
        if (BOOK_PATTERN.matcher(lower).find()) {
            return Intent.BOOK_TICKET;
        }

        if (CINEMA_PATTERN.matcher(lower).find()) {
            return Intent.QUERY_CINEMA;
        }

        // 兜底：交给 LLM Function Calling 自行判断
        return Intent.QUERY_MOVIE;
    }
}