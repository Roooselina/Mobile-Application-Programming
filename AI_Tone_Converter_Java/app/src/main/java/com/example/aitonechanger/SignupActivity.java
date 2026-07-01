package com.example.aitonechanger;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/**
 * 회원가입 화면.
 *
 * 이메일 인증 절차:
 *   1) 이메일 입력 후 [인증요청] → 6자리 코드 발송 (SMTP 미설정 시 데모 모드로 코드가 화면에 표시)
 *   2) 받은 코드를 입력하고 [확인] → 인증 완료
 *   3) 인증이 완료되어야 [회원가입] 버튼이 동작한다.
 *   이메일을 수정하면 인증이 무효화되어 다시 인증해야 한다.
 */
public class SignupActivity extends BaseActivity {

    private DatabaseHelper dbHelper;

    private EditText signEmail;
    private EditText signVerifyCode;
    private View rowVerifyCode;
    private Button btnSendCode;
    private TextView txtVerifyStatus;

    private boolean emailVerified = false;
    private String verifiedEmail = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);
        applyInsetsToRoot();
        shrinkHints();

        dbHelper = new DatabaseHelper(this);

        signEmail = findViewById(R.id.signEmail);
        signVerifyCode = findViewById(R.id.signVerifyCode);
        rowVerifyCode = findViewById(R.id.rowVerifyCode);
        btnSendCode = findViewById(R.id.btnSendCode);
        txtVerifyStatus = findViewById(R.id.txtVerifyStatus);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.txtGoLogin).setOnClickListener(v -> finish());

        // 이메일을 고치면 기존 인증을 무효화한다.
        signEmail.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (emailVerified && (verifiedEmail == null
                        || !verifiedEmail.equalsIgnoreCase(s.toString().trim()))) {
                    emailVerified = false;
                    verifiedEmail = null;
                    EmailVerifier.invalidate();
                    txtVerifyStatus.setText("이메일이 변경되어 다시 인증이 필요합니다.");
                    txtVerifyStatus.setTextColor(getColor(R.color.text_sub));
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnSendCode.setOnClickListener(v -> requestVerificationCode());
        findViewById(R.id.btnVerifyCode).setOnClickListener(v -> verifyCode());
        findViewById(R.id.btnSignup).setOnClickListener(v -> signup());
    }

    private void requestVerificationCode() {
        String email = signEmail.getText().toString().trim();

        if (email.isEmpty() || !email.contains("@")) {
            Toast.makeText(this, "올바른 이메일을 먼저 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 이미 가입된 이메일이면 코드 발송 전에 알려준다.
        if (isEmailRegistered(email)) {
            Toast.makeText(this, "이미 존재하는 회원입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendCode.setEnabled(false);
        txtVerifyStatus.setText("인증코드를 발송 중입니다...");

        EmailVerifier.requestCode(email, new EmailVerifier.SendCallback() {
            @Override
            public void onSent() {
                if (isFinishing() || isDestroyed()) return;
                btnSendCode.setEnabled(true);
                rowVerifyCode.setVisibility(View.VISIBLE);
                txtVerifyStatus.setText("인증코드를 발송했습니다. 메일함을 확인해주세요. (5분간 유효)");
                txtVerifyStatus.setTextColor(getColor(R.color.text_sub));
            }

            @Override
            public void onDemoMode(String code) {
                if (isFinishing() || isDestroyed()) return;
                btnSendCode.setEnabled(true);
                rowVerifyCode.setVisibility(View.VISIBLE);
                txtVerifyStatus.setText("(데모 모드) 아래 코드를 입력해주세요. (5분간 유효)");
                txtVerifyStatus.setTextColor(getColor(R.color.text_sub));

                // SMTP 미설정: 테스트용으로 코드를 직접 보여준다.
                new AlertDialog.Builder(SignupActivity.this)
                        .setTitle("데모 모드 인증코드")
                        .setMessage("SMTP가 설정되지 않아 메일 대신 코드를 표시합니다.\n\n인증코드: " + code
                                + "\n\n실제 메일 발송을 원하면 local.properties에 SMTP_EMAIL과 SMTP_PASSWORD를 설정해주세요.")
                        .setPositiveButton("확인", null)
                        .show();
            }

            @Override
            public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                btnSendCode.setEnabled(true);
                txtVerifyStatus.setText("인증코드 발송 실패: " + message);
            }
        });
    }

    private void verifyCode() {
        String email = signEmail.getText().toString().trim();
        String code = signVerifyCode.getText().toString().trim();

        if (code.length() != 6) {
            Toast.makeText(this, "인증코드 6자리를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (EmailVerifier.verify(email, code)) {
            emailVerified = true;
            verifiedEmail = email;
            txtVerifyStatus.setText("이메일 인증이 완료되었습니다.");
            txtVerifyStatus.setTextColor(getColor(R.color.blue));
            rowVerifyCode.setVisibility(View.GONE);
            Toast.makeText(this, "이메일 인증이 완료되었습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "인증코드가 올바르지 않거나 만료되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isEmailRegistered(String email) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE email = ?",
                new String[]{email}
        );
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    private void signup() {
        EditText signName = findViewById(R.id.signName);
        EditText signPw = findViewById(R.id.signPw);
        EditText signPw2 = findViewById(R.id.signPw2);

        String name = signName.getText().toString().trim();
        String email = signEmail.getText().toString().trim();
        String password = signPw.getText().toString().trim();
        String passwordCheck = signPw2.getText().toString().trim();

        // 빈칸 검사
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || passwordCheck.isEmpty()) {
            Toast.makeText(this, "모든 항목을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 이메일 형식 검사
        if (!email.contains("@")) {
            Toast.makeText(this, "올바른 이메일 형식이 아닙니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 이메일 인증 검사
        if (!emailVerified || verifiedEmail == null || !verifiedEmail.equalsIgnoreCase(email)) {
            Toast.makeText(this, "이메일 인증을 먼저 완료해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 비밀번호 길이 검사
        if (password.length() < 6) {
            Toast.makeText(this, "비밀번호는 6자 이상 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 비밀번호 일치 검사
        if (!password.equals(passwordCheck)) {
            Toast.makeText(this, "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 이미 존재하는 이메일인지 확인
        if (isEmailRegistered(email)) {
            Toast.makeText(this, "이미 존재하는 회원입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 회원 정보 저장 (회원가입 시 입력한 이름이 기본 이름으로 사용된다)
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("nickname", name);
        // 비밀번호는 원문 대신 SHA-256+솔트 해시로 저장 (DB 유출 대비)
        values.put("password", PasswordUtil.hash(password));

        long result = db.insert("users", null, values);

        if (result == -1) {
            Toast.makeText(this, "회원가입 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
