package com.example.aitonechanger.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Request body for Anthropic native Messages API:
 *   POST https://api.anthropic.com/v1/messages
 *
 * Note: unlike the OpenAI schema, the system prompt is a TOP-LEVEL "system"
 * field (not a message with role "system"). The messages array holds only
 * user/assistant turns.
 */
public class AnthropicRequest {

    @SerializedName("model")
    private final String model;

    @SerializedName("max_tokens")
    private final int maxTokens;

    @SerializedName("system")
    private final String system;

    @SerializedName("temperature")
    private final Double temperature;

    @SerializedName("messages")
    private final List<Message> messages;

    public AnthropicRequest(String model, int maxTokens, String system,
                            Double temperature, List<Message> messages) {
        this.model = model;
        this.maxTokens = maxTokens;
        this.system = system;
        this.temperature = temperature;
        this.messages = messages;
    }

    public static class Message {
        public static final String ROLE_USER = "user";
        public static final String ROLE_ASSISTANT = "assistant";

        @SerializedName("role")
        private final String role;

        @SerializedName("content")
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}
