package com.example.aitonechanger.data.api;

import com.example.aitonechanger.data.model.AnthropicRequest;
import com.example.aitonechanger.data.model.AnthropicResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * Retrofit interface for the Anthropic native Messages API.
 *   POST https://api.anthropic.com/v1/messages
 * Auth uses the x-api-key header plus a fixed anthropic-version header.
 */
public interface GatewayApi {

    @Headers({
            "anthropic-version: 2023-06-01",
            "content-type: application/json"
    })
    @POST("v1/messages")
    Call<AnthropicResponse> messages(
            @Header("x-api-key") String apiKey,
            @Body AnthropicRequest request
    );
}
