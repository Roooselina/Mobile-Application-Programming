package com.example.aitonechanger;

import android.app.Activity;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

public class PasswordChangeActivity extends BaseActivity {

    private DatabaseHelper dbHelper;

    private EditText editCurrentPassword;
    private EditText editNewPassword;
    private EditText editNewPasswordCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_change);
        applyInsetsToRoot();
        shrinkHints();

        dbHelper = new DatabaseHelper(this);

        editCurrentPassword = findViewById(R.id.editCurrentPassword);
        editNewPassword = findViewById(R.id.editNewPassword);
        editNewPasswordCheck = findViewById(R.id.editNewPasswordCheck);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnChangePassword).setOnClickListener(v -> changePassword());
    }

    private void changePassword() {
        String currentPassword = editCurrentPassword.getText().toString().trim();
        String newPassword = editNewPassword.getText().toString().trim();
        String newPasswordCheck = editNewPasswordCheck.getText().toString().trim();

        if (PromptData.currentUserId == -1) {
            Toast.makeText(this, "로그인 정보가 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPassword.isEmpty() || newPassword.isEmpty() || newPasswordCheck.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPassword.length() < 6) {
            Toast.makeText(this, "현재 비밀번호는 6자 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newPassword.length() < 6) {
            Toast.makeText(this, "변경할 비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentPassword.equals(newPassword)) {
            Toast.makeText(this, "현재 비밀번호와 다른 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!newPassword.equals(newPasswordCheck)) {
            Toast.makeText(this, "변경할 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isCorrectPassword = dbHelper.checkCurrentPassword(
                PromptData.currentUserId,
                currentPassword
        );

        if (!isCorrectPassword) {
            Toast.makeText(this, "현재 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean result = dbHelper.updatePassword(
                PromptData.currentUserId,
                newPassword
        );

        if (!result) {
            Toast.makeText(this, "비밀번호 변경에 실패했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "비밀번호가 변경되었습니다.", Toast.LENGTH_SHORT).show();
        finish();
    }
}