package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.common.dal.mapper.ChatSessionMapper;
import com.alipay.ticketbacked.core.model.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper MAPPER = new ObjectMapper();
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

            // 2. 意图识别（服务端规则）
            IntentDetector.Intent intent = intentDetector.detect(content);
            log.info("[Agent] 意图识别: intent={}, text={}", intent, content);

            // reject 意图直接返回固定话术
            if (intent == IntentDetector.Intent.REJECT) {
                String reply = AgentPrompts.REJECT_REPLY;
                eventCallback.accept(Map.of("type", "text", "content", reply));
                saveMessage(sessionId, "assistant", reply);
                eventCallback.accept(Map.of("type", "done", "session_id", sessionId));
                return;
            }

            // 3. 服务端槽位抽取
            Map<String, Object> extracted = slotManager.extract(content, session);
            if (!extracted.isEmpty()) {
                log.info("[Agent] 槽位抽取: {}", extracted);
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
                            } catch (Exception ignored) {}
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

            // 7. 保存 AI 回复
            saveMessage(sessionId, "assistant", fullReply);

            // 8. 推送积攒的卡片（文字回复完后再推卡片，视觉顺序更好）
            for (Map<String, Object> cardEvent : pendingCards) {
                eventCallback.accept(cardEvent);
            }

            // 9. 发送 done 事件
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
        ChatSession session = chatSessionMapper.findBySessionId(sessionId);
        if (session == null) return;

        List<Map<String, Object>> messages = parseJson(session.getMessages(), List.class);
        if (messages == null) messages = new ArrayList<>();
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("role", role);
        msg.put("content", content);
        msg.put("timestamp", LocalDateTime.now().toString());
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