package com.alipay.ticketbacked.biz.shared.ai;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.AnnotatedTypeMetadata;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class LocalChatModelConfig {

    @Bean
    @Primary
    @Conditional(LocalChatModelCondition.class)
    public ChatModel localFallbackChatModel(
            @Value("${spring.ai.openai.chat.options.model:gpt-4o-mini}") String configuredModel) {
        return new LocalFallbackChatModel(configuredModel);
    }

    static class LocalChatModelCondition implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String apiKey = context.getEnvironment().getProperty("spring.ai.openai.api-key", "");
            return apiKey.isBlank() || "local-dev-key".equals(apiKey);
        }
    }

    static class LocalFallbackChatModel implements ChatModel {

        private static final String LOCAL_REPLY = """
                当前后端已经接入了 Spring AI/OpenAI 的调用链路，但本地没有配置真实 OPENAI_API_KEY，所以我先用本地模式回复。
                配置 OPENAI_API_KEY 后重启服务，这个接口会自动切到真实大模型。""";

        private final String configuredModel;

        LocalFallbackChatModel(String configuredModel) {
            this.configuredModel = configuredModel;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return response(replyFor(prompt));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.fromIterable(chunks(replyFor(prompt), 18))
                    .map(this::response);
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return ChatOptions.builder().model(configuredModel).build();
        }

        private String replyFor(Prompt prompt) {
            String contents = prompt != null ? prompt.getContents() : "";
            if (contents != null && contents.contains("严格返回 JSON")) {
                return "{\"intent\":\"query_movie\",\"slots\":{}}";
            }
            String userText = latestUserText(prompt);
            if (userText == null || userText.isBlank()) {
                return LOCAL_REPLY;
            }
            return LOCAL_REPLY + "\n\n你刚才说的是：" + userText;
        }

        private String latestUserText(Prompt prompt) {
            if (prompt == null || prompt.getInstructions() == null) {
                return "";
            }
            List<Message> instructions = prompt.getInstructions();
            for (int i = instructions.size() - 1; i >= 0; i--) {
                Message message = instructions.get(i);
                if (message != null && message.getMessageType() != null
                        && "USER".equals(message.getMessageType().name())) {
                    return message.getText();
                }
            }
            return prompt.getContents();
        }

        private ChatResponse response(String text) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
        }

        private List<String> chunks(String text, int chunkSize) {
            List<String> result = new ArrayList<>();
            if (text == null || text.isEmpty()) {
                result.add("");
                return result;
            }
            for (int i = 0; i < text.length(); i += chunkSize) {
                result.add(text.substring(i, Math.min(text.length(), i + chunkSize)));
            }
            return result;
        }
    }
}
