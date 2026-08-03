package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.common.dal.mapper.ChatSessionMapper;
import com.alipay.ticketbacked.core.model.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import com.alipay.sofa.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Consumer;

/**
 * Agent 编排服务 — 方案A：LLM 只做导航查询，不做交易执行。
 * 手写 ReAct 循环 + chatModel.stream 真流式输出。
 */
@Service
public class ChatAgentService {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final int MAX_ITERATIONS = 5;

    private final ChatModel chatModel;
    private final ChatSessionMapper chatSessionMapper;
    private final List<ToolCallback> toolCallbacks;
    private final IntentDetector intentDetector;
    private final SlotManager slotManager;

    public ChatAgentService(ChatModel chatModel, ChatSessionMapper chatSessionMapper,
                            List<ToolCallback> toolCallbacks,
                            IntentDetector intentDetector, SlotManager slotManager) {
        this.chatModel = chatModel;
        this.chatSessionMapper = chatSessionMapper;
        this.toolCallbacks = toolCallbacks;
        this.intentDetector = intentDetector;
        this.slotManager = slotManager;
    }

    /**
     * 处理用户消息，通过 eventCallback 实时推送 SSE 事件。
     * 事件类型:
     *   text_delta — 流式文本片段 {type, content}
     *   tool_call  — 工具调用通知   {type, tool}
     *   text       — 完整文本回复   {type, content}
     *   done       — 结束           {type, session_id}
     *   error      — 错误           {type, content}
     */
    public void processMessage(String content, String sessionId, Long userId,
                               String city, Double lat, Double lng,
                               Consumer<Map<String, Object>> eventCallback) {
        try {
            // 1. Session 管理
            ChatSession session = getOrCreateSession(sessionId, userId);
            saveMessage(sessionId, "user", content);

            // 2. 意图识别（正则优先）
            IntentDetector.Intent intent = intentDetector.detect(content);
            Map<String, Object> extracted = slotManager.extract(content, session);
            log.info("[Agent] 正则识别: intent={}, slots={}, text={}", intent, extracted, content);

            // 2b. LLM 兜底：正则不够自信时调用 LLM 补充意图和槽位
            boolean needLLM = false;
            // 条件1: 意图不明确（QUERY_MOVIE 可能是兜底返回的）
            if (!isIntentConfident(content, intent) && !isGreeting(content)) {
                needLLM = true;
            }
            // 条件2: 消息明显包含信息但正则抽不到任何槽位
            if (!needLLM && extracted.isEmpty() && content.length() > 5 && !isGreeting(content)) {
                // 只有当意图不是 reject/refund/order 这种不需要槽位的才需要 LLM
                if (intent != IntentDetector.Intent.REJECT
                        && intent != IntentDetector.Intent.QUERY_ORDER) {
                    needLLM = true;
                }
            }
            // 条件3: 消息含绝对日期但正则没抽到时间槽位
            if (!needLLM && containsAbsoluteDate(content) && !extracted.containsKey("time_expression")) {
                needLLM = true;
            }

            if (needLLM) {
                log.info("[Agent] 触发 LLM 兜底抽取");
                LlmExtractionResult llmResult = llmExtract(content);
                // LLM 意图只在正则不够自信时覆盖
                if (llmResult.intent != null && !isIntentConfident(content, intent)) {
                    intent = llmResult.intent;
                    log.info("[Agent] LLM 修正意图: {}", intent);
                }
                // LLM 槽位只补充正则没抽到的，不覆盖正则已有的
                for (Map.Entry<String, Object> entry : llmResult.slots.entrySet()) {
                    if (!extracted.containsKey(entry.getKey())) {
                        extracted.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            // reject 意图走 LLM 生成有人情味的回复（流式输出）
            if (intent == IntentDetector.Intent.REJECT) {
                String rejectSystemContent = AgentPrompts.REJECT_SYSTEM_PROMPT;

                List<org.springframework.ai.chat.messages.Message> rejectMessages = new ArrayList<>();
                rejectMessages.add(new SystemMessage(rejectSystemContent));
                // 注入对话历史，让回复有上下文连续性
                List<Map<String, Object>> rejectHistory = parseJson(session.getMessages(), List.class);
                if (rejectHistory != null) {
                    for (Map<String, Object> msg : rejectHistory) {
                        String role = (String) msg.get("role");
                        String msgContent = (String) msg.get("content");
                        if ("user".equals(role) && msgContent != null) {
                            rejectMessages.add(new UserMessage(msgContent));
                        } else if ("assistant".equals(role) && msgContent != null) {
                            rejectMessages.add(new AssistantMessage(msgContent));
                        }
                    }
                }
                rejectMessages.add(new UserMessage(content));

                Prompt rejectPrompt = new Prompt(rejectMessages);
                StringBuilder rejectBuilder = new StringBuilder();
                try {
                    chatModel.stream(rejectPrompt)
                        .doOnNext(chunk -> {
                            if (chunk == null || chunk.getResult() == null
                                    || chunk.getResult().getOutput() == null) {
                                return;
                            }
                            String delta = chunk.getResult().getOutput().getText();
                            if (delta != null && !delta.isEmpty()) {
                                rejectBuilder.append(delta);
                                eventCallback.accept(Map.of("type", "text_delta", "content", delta));
                            }
                        })
                        .blockLast();
                } catch (Exception e) {
                    log.warn("[Agent] REJECT stream 异常, fallback to call: {}", e.getMessage());
                    try {
                        ChatResponse resp = chatModel.call(rejectPrompt);
                        String text = resp.getResult().getOutput().getText();
                        if (text != null && !text.isEmpty()) {
                            rejectBuilder.append(text);
                            eventCallback.accept(Map.of("type", "text_delta", "content", text));
                        }
                    } catch (Exception e2) {
                        log.error("[Agent] REJECT fallback call 也失败", e2);
                    }
                }

                String reply = rejectBuilder.toString();
                if (reply.isEmpty()) {
                    // stream 和 call 都失败时，通过 text_delta 推送兜底回复（保持打字机效果）
                    reply = "这个我帮不了你啦，我是个电影助手嘛。不过你要是想看场电影，随时来找我呀～";
                    eventCallback.accept(Map.of("type", "text_delta", "content", reply));
                }
                saveMessage(sessionId, "assistant", reply);
                eventCallback.accept(Map.of("type", "done", "session_id", sessionId));
                return;
            }

            // 3. 合并槽位到 session
            if (!extracted.isEmpty()) {
                log.info("[Agent] 最终槽位: {}", extracted);
                mergeSlots(session, extracted, city);
            }

            // 4. 构建 System Prompt
            String systemContent = AgentPrompts.AGENT_SYSTEM_PROMPT;
            systemContent += "\n\n当前用户意图: " + intent.name();

            Map<String, Object> slots = parseJson(session.getSlots(), Map.class);
            if (slots != null && !slots.isEmpty()) {
                systemContent += "\n当前槽位状态: " + slotsToString(slots);
            }
            if (city != null && !city.isBlank()) {
                systemContent += "\n用户当前定位城市: " + city;
            }
            if (lat != null && lng != null) {
                systemContent += "\n用户当前定位经纬度: lng=" + lng + ", lat=" + lat;
            }
            // 注入当前用户ID，让 LLM 调用 get_user_orders 时直接使用，无需向用户询问
            if (userId != null) {
                systemContent += "\n当前用户ID: " + userId;
            }

            // 5. 构建消息列表（含完整历史 — user + assistant）
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemContent));

            List<Map<String, Object>> history = parseJson(session.getMessages(), List.class);
            if (history != null) {
                for (Map<String, Object> msg : history) {
                    String role = (String) msg.get("role");
                    String msgContent = (String) msg.get("content");
                    if ("user".equals(role) && msgContent != null) {
                        messages.add(new UserMessage(msgContent));
                    } else if ("assistant".equals(role) && msgContent != null) {
                        messages.add(new AssistantMessage(msgContent));
                    }
                }
            }
            messages.add(new UserMessage(content));

            // 6. Agent 循环 — 手写 ReAct + stream 流式
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .withToolCallbacks(toolCallbacks)
                    .withInternalToolExecutionEnabled(false)
                    .build();

            String fullReply = "";
            boolean gotReply = false;
            List<Map<String, Object>> pendingCards = new ArrayList<>();

            for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                Prompt prompt = new Prompt(messages, options);
                log.info("[Agent] 迭代 {} 开始, messages 数量={}", iteration, messages.size());

                // 累积器
                StringBuilder textBuilder = new StringBuilder();
                Map<String, ToolCallBuilder> tcBuilders = new LinkedHashMap<>();

                try {
                    chatModel.stream(prompt)
                        .doOnNext(chunk -> {
                            if (chunk == null || chunk.getResult() == null
                                    || chunk.getResult().getOutput() == null) {
                                return;
                            }
                            AssistantMessage msg = chunk.getResult().getOutput();
                            String delta = msg.getText();
                            if (delta != null && !delta.isEmpty()) {
                                textBuilder.append(delta);
                                eventCallback.accept(Map.of("type", "text_delta", "content", delta));
                            }

                            List<AssistantMessage.ToolCall> tcs = msg.getToolCalls();
                            if (tcs != null) {
                                for (AssistantMessage.ToolCall tc : tcs) {
                                    String id = tc.id() != null ? tc.id() : "tc_" + tcBuilders.size();
                                    ToolCallBuilder tcb = tcBuilders.computeIfAbsent(id, k -> new ToolCallBuilder());
                                    if (tc.id() != null) tcb.id = tc.id();
                                    if (tc.name() != null) tcb.name = tc.name();
                                    if (tc.arguments() != null) tcb.arguments.append(tc.arguments());
                                }
                            }
                        })
                        .blockLast();
                } catch (Exception e) {
                    log.warn("[Agent] 迭代 {} stream 异常, fallback to call: {}", iteration, e.getMessage());
                    try {
                        ChatResponse resp = chatModel.call(prompt);
                        AssistantMessage am = resp.getResult().getOutput();
                        String text = am.getText();
                        if (text != null && !text.isEmpty()) {
                            textBuilder.append(text);
                            eventCallback.accept(Map.of("type", "text_delta", "content", text));
                        }
                        if (am.getToolCalls() != null) {
                            for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                                ToolCallBuilder tcb = new ToolCallBuilder();
                                tcb.id = tc.id();
                                tcb.name = tc.name();
                                tcb.arguments.append(tc.arguments() != null ? tc.arguments() : "");
                                tcBuilders.put(tc.id() != null ? tc.id() : "tc_0", tcb);
                            }
                        }
                    } catch (Exception e2) {
                        log.error("[Agent] fallback call 也失败", e2);
                    }
                }

                log.info("[Agent] 迭代 {} 完成, textLen={}, toolCalls={}", iteration,
                        textBuilder.length(), tcBuilders.size());

                // stream 完成后判断
                if (!tcBuilders.isEmpty()) {
                    // 有 tool calls：执行工具，回灌 messages，继续循环
                    List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                    AssistantMessage assistantMsg = buildAssistantMessage(tcBuilders);
                    messages.add(assistantMsg);

                    for (ToolCallBuilder tcb : tcBuilders.values()) {
                        String toolName = tcb.name;
                        String toolArgs = tcb.arguments.toString();
                        log.info("[Agent] 迭代 {} 执行工具: {} args={}", iteration, toolName, toolArgs);
                        eventCallback.accept(Map.of("type", "tool_call", "tool", toolName != null ? toolName : ""));

                        ToolCallback callback = findCallback(toolName);
                        if (callback != null) {
                            // 为 search_sessions 和 search_cinemas 注入用户定位，确保按距离过滤
                            if (("search_sessions".equals(toolName) || "search_cinemas".equals(toolName))
                                    && lat != null && lng != null) {
                                toolArgs = injectLocationIntoArgs(toolArgs, lat, lng);
                            }
                            // 为 get_user_orders 和 refund_order 强制注入当前登录用户ID，
                            // LLM 不需要也不应该自己提供 user_id（安全 + 防遗漏）
                            if (("get_user_orders".equals(toolName) || "refund_order".equals(toolName)) && userId != null) {
                                toolArgs = injectUserIdIntoArgs(toolArgs, userId);
                            }
                            String result;
                            try {
                                result = callback.call(toolArgs);
                            } catch (Exception e) {
                                log.error("[Agent] 工具执行异常: {}", toolName, e);
                                result = "{\"error\":\"工具执行异常\"}";
                            }
                            log.info("[Agent] 工具 {} 返回长度: {}", toolName, result != null ? result.length() : 0);

                            // 卡片先攒到 pendingCards，等文字回复完再推
                            collectCardEvent(toolName, result, pendingCards);

                            toolResponses.add(new ToolResponseMessage.ToolResponse(
                                    toolName != null ? toolName : "unknown",
                                    tcb.id != null ? tcb.id : "", result));
                        } else {
                            log.warn("[Agent] 未知工具: {}", toolName);
                            toolResponses.add(new ToolResponseMessage.ToolResponse(
                                    toolName != null ? toolName : "unknown",
                                    tcb.id != null ? tcb.id : "", "{\"error\":\"未知工具\"}"));
                        }
                    }
                    messages.add(new ToolResponseMessage(toolResponses));

                    // 检查工具结果是否需要消歧确认
                    boolean needConfirm = false;
                    String confirmMsg = null;
                    for (ToolResponseMessage.ToolResponse tr : toolResponses) {
                        if (tr.responseData() != null && tr.responseData().contains("need_confirmation")) {
                            needConfirm = true;
                            try {
                                Map<String, Object> parsed = MAPPER.readValue(tr.responseData(), Map.class);
                                confirmMsg = (String) parsed.get("message");
                            } catch (Exception e) {
                                log.warn("[Agent] 解析消歧消息失败", e);
                            }
                        }
                    }
                    if (needConfirm) {
                        // 强制模型这一轮只能问用户确认，不能再调工具
                        log.info("[Agent] 工具返回消歧请求, 注入确认约束");
                        messages.add(new UserMessage(
                            "重要：上面的工具返回了多个匹配结果，需要用户确认。"
                            + (confirmMsg != null ? "请向用户说：" + confirmMsg + "。" : "请让用户选择具体是哪一个。")
                            + " 本轮回复中不要再调用任何工具，直接用自然语言向用户展示选项并等待用户选择。"));
                    }

                    continue;
                }

                // 没有 tool calls：纯文本回复
                String responseContent = textBuilder.toString().trim();
                if (!responseContent.isEmpty()) {
                    fullReply = responseContent;
                    gotReply = true;
                    break;
                }
            }

            if (!gotReply) {
                fullReply = "我在帮您查询时遇到了一些问题，能再说详细一点吗？";
                eventCallback.accept(Map.of("type", "text", "content", fullReply));
                log.warn("[Agent] 超过最大迭代次数 {}", MAX_ITERATIONS);
            }

            // 7. 推送积攒的卡片（文字回复完后再推卡片，视觉顺序更好）
            //    对相同 card_type 的卡片做合并去重，避免 LLM 同时调用功能重叠的工具导致重复卡片
            List<Map<String, Object>> dedupedCards = deduplicateCards(pendingCards);

            // 8. 保存 AI 回复（连同去重后的卡片数据一起存储，供历史记录回显）
            saveMessage(sessionId, "assistant", fullReply, dedupedCards);

            // 9. 推送卡片给前端
            for (Map<String, Object> cardEvent : dedupedCards) {
                eventCallback.accept(cardEvent);
            }

            // 10. 发送 done 事件
            eventCallback.accept(Map.of("type", "done", "session_id", sessionId));

        } catch (Exception e) {
            log.error("[Agent] 对话处理异常", e);
            eventCallback.accept(Map.of("type", "error", "content", "处理出错了: " + e.getMessage()));
        }
    }

    /**
     * 工具执行后，把返回结果映射成卡片事件收集到 pendingCards 列表。
     * 由调用方在合适的时机（文本回复完后）统一推给前端。
     */
    @SuppressWarnings("unchecked")
    private void collectCardEvent(String toolName, String result, List<Map<String, Object>> pendingCards) {
        if (toolName == null || result == null || result.isBlank()) return;

        String cardType = switch (toolName) {
            case "search_movies", "recommend_movies" -> "movie_list";
            case "search_cinemas" -> "cinema_list";
            case "search_sessions" -> "session_list";
            case "get_user_orders" -> "order_list";
            case "refund_order" -> "refund_result";
            default -> null;
        };
        if (cardType == null) return;

        try {
            Object parsed = MAPPER.readValue(result, Object.class);
            List<Map<String, Object>> items;

            if (parsed instanceof List) {
                items = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;
                if (map.containsKey("error") || map.containsKey("need_confirmation")) {
                    return;
                }
                items = List.of(map);
            } else {
                return;
            }

            if (items.isEmpty()) return;

            log.info("[Agent] 收集卡片: type={}, items={}", cardType, items.size());
            pendingCards.add(Map.of(
                    "type", "card",
                    "card_type", cardType,
                    "items", items));
        } catch (Exception e) {
            log.warn("[Agent] 解析工具结果收集卡片失败: {}", toolName, e);
        }
    }

    /**
     * 对相同 card_type 的卡片做合并去重。
     * 当 LLM 在同一轮调用了 search_movies + recommend_movies 等功能重叠的工具时，
     * 会产生多张 movie_list 卡片，这里合并为一张，按 item 的 id 字段去重。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> deduplicateCards(List<Map<String, Object>> cards) {
        if (cards == null || cards.size() <= 1) return cards;

        Map<String, Map<String, Object>> cardByType = new LinkedHashMap<>();
        for (Map<String, Object> card : cards) {
            String ct = (String) card.get("card_type");
            Map<String, Object> existing = cardByType.get(ct);
            if (existing == null) {
                cardByType.put(ct, new LinkedHashMap<>(card));
            } else {
                // 合并 items，按 id 去重，保留先出现的条目（字段更完整）
                List<Map<String, Object>> merged = new ArrayList<>(
                        (List<Map<String, Object>>) existing.get("items"));
                Set<Object> seenIds = new HashSet<>();
                for (Map<String, Object> item : merged) {
                    seenIds.add(item.get("id"));
                }
                for (Map<String, Object> item : (List<Map<String, Object>>) card.get("items")) {
                    if (!seenIds.contains(item.get("id"))) {
                        merged.add(item);
                        seenIds.add(item.get("id"));
                    }
                }
                existing.put("items", merged);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>(cardByType.values());
        if (result.size() < cards.size()) {
            log.info("[Agent] 卡片去重: {} -> {}", cards.size(), result.size());
        }
        return result;
    }

    /** 获取对话历史列表 */
    public List<Map<String, Object>> listSessions(Long userId) {
        List<ChatSession> sessions = chatSessionMapper.findByUserId(userId.intValue());
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatSession s : sessions) {
            List<Map<String, Object>> messages = parseJson(s.getMessages(), List.class);
            String title = "新对话";
            String preview = "暂无消息";
            if (messages != null) {
                for (Map<String, Object> msg : messages) {
                    if ("user".equals(msg.get("role"))) {
                        String c = (String) msg.get("content");
                        if (c != null) {
                            title = c.length() > 30 ? c.substring(0, 30) : c;
                            preview = c.length() > 50 ? c.substring(0, 50) : c;
                        }
                        break;
                    }
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("session_id", s.getSessionId());
            item.put("title", title);
            item.put("preview", preview);
            item.put("message_count", messages != null ? messages.size() : 0);
            result.add(item);
        }
        return result;
    }

    /** 获取某对话的完整消息 */
    public List<Map<String, Object>> getSessionMessages(String sessionId) {
        ChatSession session = chatSessionMapper.findBySessionId(sessionId);
        if (session == null) return Collections.emptyList();
        return parseJson(session.getMessages(), List.class);
    }

    /** 删除对话 */
    public void deleteSession(String sessionId) {
        chatSessionMapper.deleteBySessionId(sessionId);
    }

    // ===== Private helpers =====

    /** LLM 抽取结果 */
    private static class LlmExtractionResult {
        IntentDetector.Intent intent = null;
        Map<String, Object> slots = new LinkedHashMap<>();
    }

    /**
     * LLM 兜底抽取意图 + 槽位。
     * 只用在正则无法覆盖的场景，不替代正则。
     * 返回 LLM 识别到的结果，调用方负责与正则结果合并。
     */
    private LlmExtractionResult llmExtract(String text) {
        LlmExtractionResult result = new LlmExtractionResult();
        try {
            String extractPrompt = """
                    从以下用户消息中抽取意图和槽位，严格返回 JSON（不要有任何额外文字）。

                    意图只能选一个:
                    - book_ticket:   购票 / 订票 / 想看某部电影
                    - refund_ticket: 退票 / 退款 / 取消订单
                    - query_movie:   查电影 / 电影推荐 / 什么电影好看
                    - query_cinema:  查影院 / 附近影院 / 哪里有IMAX
                    - query_order:   查订单 / 我的票 / 取票码
                    - reject:        闲聊 / 与购票无关

                    槽位:
                    - movie_name:     电影名称（不含书名号）
                    - cinema_name:    影院名称（不含"影院"等后缀也行，保留用户原话）
                    - time_expression: 时间表达，包括绝对日期如"7月15号"、"下周五"，相对日期如"明天"、"后天"，时间段如"晚上"、"下午3点"
                    - ticket_count:   购票数量（纯数字字符串）

                    规则:
                    1. 没有出现的槽位不要返回对应 key。
                    2. intent 必填，slots 可空 {}。
                    3. 严格 JSON 格式: {"intent":"book_ticket","slots":{"movie_name":"沙丘3","time_expression":"7月15号晚上"}}

                    用户消息: %s
                    """;

            Prompt prompt = new Prompt(String.format(extractPrompt, text));
            ChatResponse resp = chatModel.call(prompt);
            String raw = resp.getResult().getOutput().getText();

            // 提取 JSON（兼容 LLM 可能包裹 ```json ... ``` 的情况）
            if (raw != null) {
                int start = raw.indexOf('{');
                int end = raw.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    String json = raw.substring(start, end + 1);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed = MAPPER.readValue(json, Map.class);

                    String intentStr = (String) parsed.get("intent");
                    if (intentStr != null) {
                        try {
                            result.intent = IntentDetector.Intent.valueOf(intentStr.toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                            // LLM 返回了未知意图，保持 null
                        }
                    }

                    @SuppressWarnings("unchecked")
                    Map<String, Object> llmSlots = (Map<String, Object>) parsed.get("slots");
                    if (llmSlots != null) {
                        result.slots.putAll(llmSlots);
                    }
                }
            }
            log.info("[Agent] LLM兜底抽取: intent={}, slots={}", result.intent, result.slots);
        } catch (Exception e) {
            log.warn("[Agent] LLM兜底抽取异常, 回退到纯正则结果: {}", e.getMessage());
        }
        return result;
    }

    /** 判断正则意图识别是否命中了明确的模式（非兜底） */
    private boolean isIntentConfident(String text, IntentDetector.Intent intent) {
        if (intent == IntentDetector.Intent.REFUND_TICKET
                || intent == IntentDetector.Intent.BOOK_TICKET
                || intent == IntentDetector.Intent.QUERY_CINEMA
                || intent == IntentDetector.Intent.QUERY_ORDER) {
            return true;
        }
        // QUERY_MOVIE 可能是真实命中也可能是兜底，需要二次确认
        if (intent == IntentDetector.Intent.QUERY_MOVIE) {
            String lower = text.trim();
            return lower.matches(".*(什么电影|有什么电影|电影推荐|推荐.*电影|评分.*高|最近.*上映|热映|正在.*映|好看.*电影).*");
        }
        return false;
    }

    /** 判断是否为打招呼 */
    private boolean isGreeting(String text) {
        String lower = text.trim().toLowerCase();
        return lower.length() <= 6 && (lower.contains("你好") || lower.contains("hi")
                || lower.contains("hello") || lower.contains("在吗"));
    }

    /** 判断消息是否包含绝对日期（正则可能无法抽取的） */
    private boolean containsAbsoluteDate(String text) {
        return text != null && text.matches(".*\\d{1,2}\\s*月\\s*\\d{1,2}\\s*[号日].*");
    }

    private static class ToolCallBuilder {
        String id;
        String name;
        StringBuilder arguments = new StringBuilder();
    }

    private AssistantMessage buildAssistantMessage(Map<String, ToolCallBuilder> tcBuilders) {
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        for (ToolCallBuilder tcb : tcBuilders.values()) {
            toolCalls.add(new AssistantMessage.ToolCall(
                    tcb.id != null ? tcb.id : "",
                    "function",
                    tcb.name != null ? tcb.name : "unknown",
                    tcb.arguments.toString()
            ));
        }
        return new AssistantMessage("", Map.of(), toolCalls);
    }

    private ChatSession getOrCreateSession(String sessionId, Long userId) {
        ChatSession session = chatSessionMapper.findBySessionId(sessionId);
        if (session != null) return session;

        session = new ChatSession();
        session.setSessionId(sessionId);
        session.setUserId(userId.intValue());
        session.setSlots("{}");
        session.setLastIntent("");
        session.setContext("{}");
        session.setMessages("[]");
        session.setGmtExpire(LocalDateTime.now().plusMinutes(60));
        chatSessionMapper.insert(session);
        return session;
    }

    @SuppressWarnings("unchecked")
    private void saveMessage(String sessionId, String role, String content) {
        saveMessage(sessionId, role, content, null);
    }

    @SuppressWarnings("unchecked")
    private void saveMessage(String sessionId, String role, String content, List<Map<String, Object>> cards) {
        ChatSession session = chatSessionMapper.findBySessionId(sessionId);
        if (session == null) return;

        List<Map<String, Object>> messages = parseJson(session.getMessages(), List.class);
        if (messages == null) messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        msg.put("timestamp", LocalDateTime.now().toString());
        if (cards != null && !cards.isEmpty()) {
            msg.put("cards", cards);
        }
        messages.add(msg);

        try {
            chatSessionMapper.appendMessage(sessionId, MAPPER.writeValueAsString(messages));
        } catch (Exception e) {
            log.error("保存消息失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void mergeSlots(ChatSession session, Map<String, Object> extracted, String city) {
        try {
            Map<String, Object> existing = parseJson(session.getSlots(), Map.class);
            if (existing == null) existing = new HashMap<>();

            // 覆盖语义：movie_name 变 → 清 cinema_name + 派生槽位
            if (extracted.containsKey("movie_name") && !Objects.equals(
                    extracted.get("movie_name"), existing.get("movie_name"))) {
                existing.remove("cinema_name");
                existing.remove("session_id");
            }
            // cinema_name 变 → 清派生槽位
            if (extracted.containsKey("cinema_name") && !Objects.equals(
                    extracted.get("cinema_name"), existing.get("cinema_name"))) {
                existing.remove("session_id");
            }

            existing.putAll(extracted);
            String slotsJson = MAPPER.writeValueAsString(existing);
            session.setSlots(slotsJson);
            chatSessionMapper.updateSlotsAndContext(
                    session.getSessionId(), slotsJson,
                    session.getLastIntent() != null ? session.getLastIntent() : "",
                    session.getContext() != null ? session.getContext() : "{}");
        } catch (Exception e) {
            log.error("合并槽位失败", e);
        }
    }

    private ToolCallback findCallback(String name) {
        if (name == null) return null;
        return toolCallbacks.stream()
                .filter(tc -> tc.getToolDefinition() != null && name.equals(tc.getToolDefinition().name()))
                .findFirst()
                .orElse(null);
    }

    /** 向 search_sessions 的工具参数 JSON 中注入用户经纬度 */
    @SuppressWarnings("unchecked")
    private String injectLocationIntoArgs(String toolArgs, Double lat, Double lng) {
        try {
            Map<String, Object> args = MAPPER.readValue(toolArgs, Map.class);
            args.put("lat", lat);
            args.put("lng", lng);
            return MAPPER.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("[Agent] 注入定位失败, 原始参数: {}", toolArgs);
            return toolArgs;
        }
    }

    /** 向 get_user_orders 的工具参数 JSON 中强制注入当前登录用户ID */
    @SuppressWarnings("unchecked")
    private String injectUserIdIntoArgs(String toolArgs, Long userId) {
        try {
            Map<String, Object> args = MAPPER.readValue(toolArgs, Map.class);
            // 无条件覆盖 LLM 可能提供（或缺失）的 user_id，确保只能查当前登录用户的订单
            args.put("user_id", userId);
            return MAPPER.writeValueAsString(args);
        } catch (Exception e) {
            log.warn("[Agent] 注入用户ID失败, 原始参数: {}", toolArgs);
            return toolArgs;
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T parseJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    private String slotsToString(Map<String, Object> slots) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : slots.entrySet()) {
            if (e.getValue() != null) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
        }
        return sb.toString();
    }
}