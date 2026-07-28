package com.alipay.ticketbacked.web.controller;

import com.alipay.ticketbacked.biz.shared.ai.ChatAgentService;
import com.alipay.ticketbacked.core.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话接口 — 对应 Python /api/chat
 * SSE 流式返回对话回复。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatAgentService chatAgentService;
    private final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatAgentService chatAgentService) {
        this.chatAgentService = chatAgentService;
    }

    /**
     * 发送消息，SSE 流式返回对话回复
     */
    @PostMapping("/send")
    public SseEmitter sendMessage(
            @RequestParam String content,
            @RequestParam(required = false) String session_id,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            HttpServletRequest request) {

        User user = (User) request.getAttribute("currentUser");
        if (session_id == null || session_id.isBlank()) {
            session_id = UUID.randomUUID().toString();
        }

        final String sessionId = session_id;
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时

        executor.execute(() -> {
            try {
                List<Map<String, Object>> events = chatAgentService.processMessage(
                        content, sessionId, user.getId(), city, lat, lng);

                for (Map<String, Object> event : events) {
                    String json = MAPPER.writeValueAsString(event);
                    emitter.send(SseEmitter.event().data(json));
                }
                emitter.complete();
            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event().data(
                            MAPPER.writeValueAsString(Map.of("type", "error", "content", "处理出错了"))));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * 获取对话历史列表 — 对应 Python /api/chat/sessions
     */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions(HttpServletRequest request) {
        User user = (User) request.getAttribute("currentUser");
        List<Map<String, Object>> sessions = chatAgentService.listSessions(user.getId());
        return Map.of("items", sessions, "total", sessions.size());
    }

    /**
     * 获取某对话的完整消息 — 对应 Python /api/chat/sessions/{id}/messages
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Map<String, Object> getSessionMessages(@PathVariable String sessionId) {
        List<Map<String, Object>> messages = chatAgentService.getSessionMessages(sessionId);
        return Map.of("messages", messages, "session_id", sessionId);
    }

    /**
     * 删除对话 — 对应 Python /api/chat/sessions/{id}
     */
    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId) {
        chatAgentService.deleteSession(sessionId);
        return Map.of("message", "对话已删除");
    }
}