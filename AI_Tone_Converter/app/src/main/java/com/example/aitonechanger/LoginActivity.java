package com.example.aitonechanger;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class LoginActivity extends BaseActivity {

    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        applyInsetsToRoot();
        shrinkHints();

        dbHelper = new DatabaseHelper(this);

        EditText loginEmail = findViewById(R.id.loginEmail);
        EditText loginPassword = findViewById(R.id.loginPassword);

        findViewById(R.id.btnLogin).setOnClickListener(v -> {

            String email = loginEmail.getText().toString().trim();
            String password = loginPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!email.contains("@")) {
                Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (password.length() < 6) {
                Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // DB 조회 (비밀번호는 해시로 저장되어 있어 코드에서 검증)
            SQLiteDatabase db = dbHelper.getReadableDatabase();

            Cursor cursor = db.rawQuery(
                    "SELECT id, name, password FROM users WHERE email = ?",
                    new String[]{email}
            );

            // 로그인 성공
            if (cursor.moveToFirst() && PasswordUtil.verify(password, cursor.getString(2))) {

                int userId = cursor.getInt(0);

                PromptData.currentUserId = userId;
                Session.setLoggedIn(this, userId);

                Toast.makeText(this, "로그인을 성공하였습니다.", Toast.LENGTH_SHORT).show();

                PromptData.selectedPromptId = null;

                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                finish();

            } else {
                Toast.makeText(this, "이메일 또는 비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            }

            cursor.close();
        });

        TextView goSignup = findViewById(R.id.txtGoSignup);

        goSignup.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignupActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.txtForgotPassword).setOnClickListener(v -> startPasswordReset());
    }

    // ----- 비밀번호 찾기 / 재설정 (이메일 6자리 코드 인증) -----

    /** 1단계: 가입 이메일 입력 → 인증코드 발송 */
    private void startPasswordReset() {
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("가입한 이메일 주소");
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setBackgroundResource(R.drawable.rounded_input);
        input.setPadding(dp(12), dp(12), dp(12), dp(12));

        android.widget.LinearLayout wrap = new android.widget.LinearLayout(this);
        wrap.setOrientation(android.widget.LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);
        wrap.addView(input, new android.widget.LinearLayout.LayoutParams(-1, -2));

        new android.app.AlertDialog.Builder(this)
                .setTitle("비밀번호 재설정")
                .setMessage("가입한 이메일로 인증코드를 보내드려요.")
                .setView(wrap)
                .setNegativeButton("취소", null)
                .setPositiveButton("인증코드 발송", (d, w) -> {
                    String email = input.getText().toString().trim();
                    if (email.isEmpty() || !email.contains("@")) {
                        Toast.makeText(this, "올바른 이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!isEmailRegistered(email)) {
                        Toast.makeText(this, "가입되지 않은 이메일입니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendResetCode(email);
                })
                .show();
    }

    private boolean isEmailRegistered(String email) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE email = ?", new String[]{email});
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private void sendResetCode(String email) {
        EmailVerifier.requestCode(email, new EmailVerifier.SendCallback() {
            @Override
            public void onSent() {
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(LoginActivity.this,
                        "인증코드를 발송했습니다. 메일함을 확인해주세요.", Toast.LENGTH_LONG).show();
                showResetForm(email);
            }

            @Override
            public void onDemoMode(String code) {
                if (isFinishing() || isDestroyed()) return;
                new android.app.AlertDialog.Builder(LoginActivity.this)
                        .setTitle("데모 모드 인증코드")
                        .setMessage("SMTP가 설정되지 않아 코드를 표시합니다.\n\n인증코드: " + code)
                        .setPositiveButton("확인", (d, w) -> showResetForm(email))
                        .show();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(LoginActivity.this, "발송 실패: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 2단계: 코드 + 새 비밀번호 입력 → 재설정 */
    private void showResetForm(String email) {
        android.widget.LinearLayout wrap = new android.widget.LinearLayout(this);
        wrap.setOrientation(android.widget.LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);

        final android.widget.EditText codeInput = resetField("인증코드 6자리");
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final android.widget.EditText pwInput = resetField("새 비밀번호 (6자 이상)");
        pwInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final android.widget.EditText pw2Input = resetField("새 비밀번호 확인");
        pw2Input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);

        wrap.addView(codeInput, fieldLp(0));
        wrap.addView(pwInput, fieldLp(10));
        wrap.addView(pw2Input, fieldLp(10));

        new android.app.AlertDialog.Builder(this)
                .setTitle("새 비밀번호 설정")
                .setView(wrap)
                .setNegativeButton("취소", null)
                .setPositiveButton("변경하기", (d, w) -> {
                    String code = codeInput.getText().toString().trim();
                    String pw = pwInput.getText().toString().trim();
                    String pw2 = pw2Input.getText().toString().trim();

                    if (pw.length() < 6) {
                        Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!pw.equals(pw2)) {
                        Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!EmailVerifier.verify(email, code)) {
                        Toast.makeText(this, "인증코드가 올바르지 않거나 만료되었습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean ok = dbHelper.updatePasswordByEmail(email, PasswordUtil.hash(pw));
                    Toast.makeText(this,
                            ok ? "비밀번호가 변경되었습니다. 새 비밀번호로 로그인해주세요." : "변경에 실패했습니다.",
                            Toast.LENGTH_LONG).show();
                })
                .show();
    }

    private android.widget.EditText resetField(String hint) {
        android.widget.EditText e = new android.widget.EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
        e.setTextSize(14);
        e.setBackgroundResource(R.drawable.rounded_input);
        e.setPadding(dp(12), dp(12), dp(12), dp(12));
        return e;
    }

    private android.widget.LinearLayout.LayoutParams fieldLp(int topDp) {
        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        return lp;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}