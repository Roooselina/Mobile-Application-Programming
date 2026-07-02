package com.example.aitonechanger.data.api;

import com.example.aitonechanger.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Singleton holder for the Retrofit client and GatewayApi instance.
 * Base URL comes from BuildConfig.GATEWAY_BASE_URL (Anthropic API by default).
 * Auth headers are redacted from logs.
 */
public final class RetrofitClient {

    private static volatile RetrofitClient instance;

    private final GatewayApi api;
    private final String baseUrl;

    private RetrofitClient() {
        this.baseUrl = normalizeBaseUrl(BuildConfig.GATEWAY_BASE_URL);

        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        logging.redactHeader("Authorization");
        logging.redactHeader("x-api-key");

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        Gson gson = new GsonBuilder()
                .disableHtmlEscaping()
                .create();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        this.api = retrofit.create(GatewayApi.class);
    }

    public static RetrofitClient getInstance() {
        if (instance == null) {
            synchronized (RetrofitClient.class) {
                if (instance == null) {
                    instance = new RetrofitClient();
                }
            }
        }
        return instance;
    }

    public GatewayApi getApi() {
        return api;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    private static String normalizeBaseUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "https://api.anthropic.com/";
        }
        return url.endsWith("/") ? url : url + "/";
    }
}
