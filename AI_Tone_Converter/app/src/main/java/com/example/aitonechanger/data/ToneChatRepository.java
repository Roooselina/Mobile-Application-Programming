package com.example.aitonechanger.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.aitonechanger.AppPrefs;
import com.example.aitonechanger.BuildConfig;
import com.example.aitonechanger.PromptData;
import com.example.aitonechanger.data.api.GatewayApi;
import com.example.aitonechanger.data.api.RetrofitClient;
import com.example.aitonechanger.data.model.AnthropicRequest;
import com.example.aitonechanger.data.model.AnthropicResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Tone-correction repository using the Anthropic native Messages API.
 *
 * Builds the system prompt (top-level "system" field), assembles the
 * user/assistant message history, calls /v1/messages, extracts the text,
 * and delivers the result on the main thread.
 *
 * Config (BuildConfig, from local.properties): ANTHROPIC_API_KEY, GATEWAY_MODEL.
 */
public class ToneChatRepository {

    private static final String TAG = "ToneChatRepository";
    private static final double TEMPERATURE = 0.3;

    private final GatewayApi api;
    private final Handler mainHandler;

    public ToneChatRepository() {
        this.api = RetrofitClient.getInstance().getApi();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public interface CorrectionCallback {
        void onSuccess(String correctedText);

        void onError(String errorMessage);
    }

    public void correctTone(
            Context context,
            String recipient,
            List<String> keywords,
            String persona,
            String memoryInfo,
            List<PromptData.ChatMessage> history,
            String userInput,
            CorrectionCallback callback
    ) {
        String apiKey = BuildConfig.ANTHROPIC_API_KEY;
        String model = BuildConfig.GATEWAY_MODEL;

        if (apiKey == null || apiKey.isEmpty()) {
            mainHandler.post(() -> callback.onError(
                    "API 키가 설정되지 않았습니다. local.properties의 ANTHROPIC_API_KEY를 확인하세요."));
            return;
        }
        if (userInput == null || userInput.trim().isEmpty()) {
            mainHandler.post(() -> callback.onError("입력이 비어 있습니다."));
            return;
        }

        int length = AppPrefs.getLength(context);
        int strength = AppPrefs.getStrength(context);
        int maxTokens = maxTokensFor(length);

        // "나에 대한 정보"가 비어 있으면 설정의 사용자 프로필(없으면 계정 이름)을 대신 사용한다.
        String effectivePersona = persona;
        if (effectivePersona == null || effectivePersona.trim().isEmpty()) {
            effectivePersona = com.example.aitonechanger.ProfileDefaults.buildFallbackAboutMe(context);
        }

        String systemPrompt = TonePromptBuilder.build(
                recipient,
                keywords,
                effectivePersona,
                memoryInfo,
                strength,
                length,
                com.example.aitonechanger.ProfileDefaults.resolveName(context),
                AppPrefs.getAffiliation(context),
                AppPrefs.getPosition(context),
                AppPrefs.getForbidden(context)
        );

        List<AnthropicRequest.Message> messages = buildMessages(history, userInput.trim());

        AnthropicRequest request =
                new AnthropicRequest(model, maxTokens, systemPrompt, TEMPERATURE, messages);

        Call<AnthropicResponse> call = api.messages(apiKey, request);

        call.enqueue(new Callback<AnthropicResponse>() {
            @Override
            public void onResponse(Call<AnthropicResponse> call,
                                   Response<AnthropicResponse> response) {
                if (!response.isSuccessful()) {
                    String body = "";
                    if (response.errorBody() != null) {
                        try {
                            body = response.errorBody().string();
                        } catch (IOException e) {
                            body = "(error body read failed)";
                        }
                    }
                    final int code = response.code();
                    final String rawBody = body;
                    Log.e(TAG, "HTTP " + code + " - " + rawBody);
                    mainHandler.post(() -> callback.onError(friendlyError(code, rawBody)));
                    return;
                }

                AnthropicResponse b = response.body();
                String text = (b == null) ? "" : b.getText();
                if (text == null || text.trim().isEmpty()) {
                    mainHandler.post(() -> callback.onError("응답 내용이 비어 있습니다."));
                    return;
                }

                String cleaned = sanitize(text);
                mainHandler.post(() -> callback.onSuccess(cleaned));
            }

            @Override
            public void onFailure(Call<AnthropicResponse> call, Throwable t) {
                Log.e(TAG, "network failure", t);
                mainHandler.post(() -> callback.onError("네트워크 오류: " + t.getMessage()));
            }
        });
    }

    private List<AnthropicRequest.Message> buildMessages(
            List<PromptData.ChatMessage> history, String userInput) {
        List<AnthropicRequest.Message> messages = new ArrayList<>();

        if (history != null) {
            for (PromptData.ChatMessage m : history) {
                String role = "user".equals(m.sender)
                        ? AnthropicRequest.Message.ROLE_USER
                        : AnthropicRequest.Message.ROLE_ASSISTANT;
                messages.add(new AnthropicRequest.Message(role, m.displayText()));
            }
        }

        messages.add(new AnthropicRequest.Message(
                AnthropicRequest.Message.ROLE_USER, userInput));
        return messages;
    }

    /**
     * Converts an Anthropic HTTP error into a user-friendly Korean message.
     */
    private String friendlyError(int code, String body) {
        String b = body == null ? "" : body;
        if (b.contains("credit") || b.contains("크레딧")) {
            return "API 크레딧이 부족합니다. Anthropic 콘솔에서 결제/크레딧을 확인해주세요.";
        }
        if (code == 401) {
            return "API 키 인증에 실패했습니다. local.properties의 키를 확인해주세요. (HTTP 401)";
        }
        if (code == 400) {
            return "요청 형식 오류입니다. 모델명 또는 max_tokens 값을 확인해주세요. (HTTP 400)";
        }
        if (code == 429) {
            return "요청이 너무 많습니다. 잠시 후 다시 시도해주세요. (HTTP 429)";
        }
        if (code == 529) {
            return "서버가 일시적으로 혼잡합니다. 잠시 후 다시 시도해주세요. (HTTP 529)";
        }
        return "요청에 실패했습니다. (HTTP " + code + ")";
    }

    private int maxTokensFor(int length) {
        // Claude는 max_tokens가 곧 출력 상한이며(추론 토큰 예산 이슈 없음),
        // claude-sonnet-4-6은 긴 출력을 지원한다. 내용에 따라 짧게/길게 모두 가능하도록 둔다.
        switch (length) {
            case AppPrefs.LENGTH_SHORT:
                return 4096;
            case AppPrefs.LENGTH_DETAILED:
                return 16000;
            case AppPrefs.LENGTH_NORMAL:
            default:
                return 8192;
        }
    }

    private String sanitize(String text) {
        if (text == null) {
            return "";
        }
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0 && t.endsWith("```")) {
                t = t.substring(nl + 1, t.length() - 3).trim();
            }
        }
        if (t.length() >= 2) {
            char f = t.charAt(0);
            char l = t.charAt(t.length() - 1);
            if ((f == '"' && l == '"') || (f == '\'' && l == '\'')) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }
}
