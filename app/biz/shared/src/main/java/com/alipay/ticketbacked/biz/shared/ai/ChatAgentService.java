package com.alipay.ticketbacked.biz.shared.ai;

import com.alipay.ticketbacked.common.dal.mapper.ChatSessionMapper;
import com.alipay.ticketbacked.common.util.JsonUtil;
import com.alipay.ticketbacked.core.model.ChatSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import com.alipay.sofa.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Agent 编排服务 — 对应 Python api/chat.py 的核心对话逻辑
 * 使用 SOFA AI ChatModel + Function Calling 实现 Agent 循环。
 */
@Service
public class ChatAgentService {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_ITERATIONS = 4;

    private final ChatModel chatModel;
    private final ChatSessionMapper chatSessionMapper;
    private final List<ToolCallback> toolCallbacks;

    public ChatAgentService(ChatModel chatModel, ChatSessionMapper chatSessionMapper,
                            List<ToolCallback> toolCallbacks) {
        this.chatModel = chatModel;
        this.chatSessionMapper = chatSessionMapper;
        this.toolCallbacks = toolCallbacks;
    }

    /**
     * 处理用户消息，返回 SSE 事件列表。
     * 每个事件是一个 Map: {type: "text"/"card"/"done", ...}
     */
    public List<Map<String, Object>> processMessage(String content, String sessionId, Long userId,
                                                     String city, Double lat, Double lng) {
        List<Map<String, Object>> events = new ArrayList<>();

        try {
            // 1. Session 管理
            ChatSession session = getOrCreateSession(sessionId, userId);
            saveMessage(sessionId, "user", content);

            // 2. 构建 System Prompt
            String systemContent = AgentPrompts.AGENT_SYSTEM_PROMPT;
            Map<String, Object> slots = parseJson(session.getSlots(), Map.class);
            if (slots != null && !slots.isEmpty()) {
                systemContent += "\n\n当前槽位状态: " + slotsToString(slots);
            }
            if (city != null && !city.isBlank()) {
                systemContent += "\n用户当前定位城市: " + city;
            }

            // 3. 构建消息列表
            List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemContent));

            // 加载历史消息
            List<Map<String, Object>> history = parseJson(session.getMessages(), List.class);
            if (history != null) {
                for (Map<String, Object> msg : history) {
                    String role = (String) msg.get("role");
                    String msgContent = (String) msg.get("content");
                    if ("user".equals(role)) {
                        messages.add(new UserMessage(msgContent));
                    }
                    // 跳过 assistant 消息以避免太长
                }
            }
            messages.add(new UserMessage(content));

            // 4. Agent 循环 — 使用 SOFA AI ChatModel + Tool Calling
            String fullReply = "";
            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .withToolCallbacks(toolCallbacks)
                    .withInternalToolExecutionEnabled(true)
                    .build();
            for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
                Prompt prompt = new Prompt(messages, options);
                ChatResponse response = chatModel.call(prompt);
                String responseContent = response.getResult().getOutput().getText();

                if (responseContent != null && !responseContent.isBlank()) {
                    fullReply = responseContent;
                    events.add(Map.of("type", "text", "content", responseContent));
                    break; // 文本回复完成
                }

                // 如果没有文本，可能是 function call（SOFA AI 自动执行了 function）
                // 简化处理：直接取返回内容
                if (responseContent == null || responseContent.isBlank()) {
                    fullReply = "好的，请问还有什么我可以帮您的？";
                    events.add(Map.of("type", "text", "content", fullReply));
                    break;
                }
            }

            // 5. 保存 AI 回复
            saveMessage(sessionId, "assistant", fullReply);

            // 6. 发送 done 事件
            events.add(Map.of("type", "done", "session_id", sessionId));

        } catch (Exception e) {
            log.error("[Agent] 对话处理异常", e);
            events.add(Map.of("type", "error", "content", "处理出错了: " + e.getMessage()));
        }

        return events;
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
                        title = c.length() > 30 ? c.substring(0, 30) : c;
                        preview = c.length() > 50 ? c.substring(0, 50) : c;
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
        session.setGmtExpire(LocalDateTime.now().plusMinutes(30));
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