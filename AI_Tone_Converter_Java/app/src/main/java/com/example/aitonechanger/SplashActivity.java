package com.example.aitonechanger;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

public class SplashActivity extends BaseActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        applyInsetsToRoot();

        // 로고를 잠깐 보여준 뒤, 로그인 상태면 바로 메인(프롬프트)으로, 아니면 로그인으로 이동
        new Handler(Looper.getMainLooper()).postDelayed(this::route, 1500);
    }

    private void route() {
        int uid = Session.getUserId(this);
        boolean valid = uid > 0 && new DatabaseHelper(this).userExists(uid);

        Intent intent;
        if (valid) {
            // 로그인 유지: 로그인 화면을 건너뛰고 바로 프롬프트 화면으로
            PromptData.currentUserId = uid;
            PromptData.selectedPromptId = null;
            intent = new Intent(this, MainActivity.class);
        } else {
            Session.clear(this);
            intent = new Intent(this, LoginActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
