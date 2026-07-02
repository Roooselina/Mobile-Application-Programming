package com.example.aitonechanger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class ProfileEditActivity extends BaseActivity {

    private DatabaseHelper dbHelper;

    private EditText editName;
    private EditText editEmail;
    private EditText editNickname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_edit);
        applyInsetsToRoot();
        shrinkHints();

        dbHelper = new DatabaseHelper(this);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editNickname = findViewById(R.id.editNickname);

        loadUserInfo();

        findViewById(R.id.btnUpdateProfile).setOnClickListener(v -> updateProfile());

        // 계정 보안: 비밀번호 변경은 계정정보 화면에서 진행
        findViewById(R.id.btnChangePasswordEntry).setOnClickListener(v ->
                startActivity(new Intent(this, PasswordChangeActivity.class))
        );

        findViewById(R.id.btnDeleteAccount).setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("회원 탈퇴")
                .setMessage("탈퇴하면 계정과 모든 프롬프트, 대화 기록이 영구적으로 삭제되며 되돌릴 수 없습니다.\n\n정말 탈퇴하시겠습니까?")
                .setNegativeButton("취소", null)
                .setPositiveButton("탈퇴하기", (d, w) -> deleteAccount())
                .show();
    }

    private void deleteAccount() {
        int userId = PromptData.currentUserId;
        boolean ok = dbHelper.deleteUser(userId);
        if (!ok) {
            Toast.makeText(this, "회원 탈퇴에 실패했습니다. 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Session.clear(this);
        PromptData.currentUserId = -1;
        PromptData.selectedPromptId = null;
        PromptData.editingPromptId = null;
        PromptData.prompts.clear();

        Toast.makeText(this, "회원 탈퇴가 완료되었습니다.", Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }

    private void loadUserInfo() {
        if (PromptData.currentUserId == -1) {
            Toast.makeText(this, "로그인 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String[] userInfo = dbHelper.getUserProfile(PromptData.currentUserId);

        editName.setText(userInfo[0]);
        editEmail.setText(userInfo[1]);
        editNickname.setText(userInfo[2]);
    }

    private void updateProfile() {
        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String nickname = editNickname.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty() || nickname.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!email.contains("@")) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean result = dbHelper.updateUserProfile(
                PromptData.currentUserId,
                name,
                email,
                nickname
        );

        if (!result) {
            Toast.makeText(this, "내 정보 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "내 정보가 수정되었습니다.", Toast.LENGTH_SHORT).show();
        finish();
    }
}