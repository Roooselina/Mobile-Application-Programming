package com.example.aitonechanger.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Response body for the Anthropic native Messages API.
 * The assistant text lives in content[] blocks of type "text".
 */
public class AnthropicResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("model")
    private String model;

    @SerializedName("stop_reason")
    private String stopReason;

    @SerializedName("content")
    private List<ContentBlock> content;

    @SerializedName("error")
    private ApiError error;

    public List<ContentBlock> getContent() {
        return content;
    }

    public String getStopReason() {
        return stopReason;
    }

    public ApiError getError() {
        return error;
    }

    /** Concatenates all text blocks into one string. */
    public String getText() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : content) {
            if (block != null && "text".equals(block.type) && block.text != null) {
                sb.append(block.text);
            }
        }
        return sb.toString();
    }

    public static class ContentBlock {
        @SerializedName("type")
        public String type;

        @SerializedName("text")
        public String text;
    }

    public static class ApiError {
        @SerializedName("type")
        public String type;

        @SerializedName("message")
        public String message;
    }
}
