package com.example.aitonechanger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

public class ProfileActivity extends BaseActivity {

    private DatabaseHelper dbHelper;

    private TextView txtName;
    private TextView txtEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        applyInsetsToRoot();

        dbHelper = new DatabaseHelper(this);

        txtName = findViewById(R.id.txtProfileName);
        txtEmail = findViewById(R.id.txtProfileEmail);

        loadProfileInfo();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnEditInfo).setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileEditActivity.class);
            startActivity(intent);
        });

        // 비밀번호 변경은 '계정정보(내 정보 수정)' 화면에서 진행한다.

        findViewById(R.id.btnSetting).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        findViewById(R.id.btnLogout).setOnClickListener(v -> showLogoutDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileInfo();
    }

    private void loadProfileInfo() {
        if (PromptData.currentUserId == -1) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] userInfo = dbHelper.getUserProfile(PromptData.currentUserId);

        String name = userInfo[0];
        String email = userInfo[1];

        txtName.setText(name);
        txtEmail.setText(email);
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("로그아웃", (dialog, which) -> logout())
                .show();
    }

    private void logout() {
        Session.clear(this);
        PromptData.currentUserId = -1;
        PromptData.selectedPromptId = null;
        PromptData.editingPromptId = null;
        PromptData.prompts.clear();

        Toast.makeText(this, "로그아웃되었습니다", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                        Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }
}