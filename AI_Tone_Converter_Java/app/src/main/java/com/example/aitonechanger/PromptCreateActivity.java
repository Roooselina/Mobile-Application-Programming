package com.example.aitonechanger;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 새 프롬프트 생성 1단계 (팝업).
 *
 * AppDialogTheme 적용: 뒤 화면은 어둡게(dim) 깔리고 카드만 떠 있는 다이얼로그 형태.
 *
 * 입력 항목:
 *   - 대화 상대 (필수)
 *   - 나에 대한 정보 (선택): 비워두면 설정의 사용자 프로필,
 *     그것도 없으면 계정 이름(회원가입/내 정보 수정의 이름)이 자동 사용된다.
 *
 * "다음"을 누르면 2단계 팝업(PromptCreateKeywordActivity)으로 넘어간다.
 */
public class PromptCreateActivity extends BaseActivity {

    public static final String EXTRA_RECIPIENT = "extra_recipient";
    public static final String EXTRA_ABOUT_ME = "extra_about_me";
    public static final String EXTRA_GROUP = "extra_group";

    private EditText editName;
    private EditText editPersona;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_create);

        editName = findViewById(R.id.editPromptName);
        editPersona = findViewById(R.id.editPersona);

        shrinkHints();
        showDefaultProfileHint();

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnNext).setOnClickListener(v -> goNext());
    }

    /** "비워두면 ~가 사용돼요" 안내에 실제 기본 이름을 보여준다. */
    private void showDefaultProfileHint() {
        TextView hint = findViewById(R.id.txtPersonaHint);

        String defaultName = ProfileDefaults.resolveName(this);

        if (defaultName != null && !defaultName.isEmpty()) {
            hint.setText("비워두면 설정에 저장된 내 정보(이름: " + defaultName + ")가 자동으로 사용돼요.");
        }
    }

    private void goNext() {
        String name = editName.getText().toString().trim();
        String persona = editPersona.getText().toString().trim();

        if (PromptData.currentUserId == -1) {
            Toast.makeText(this, "로그인 정보가 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (name.isEmpty()) {
            Toast.makeText(this, "대화 상대를 입력해주세요.", Toast.LENGTH_SHORT).show();
            editName.requestFocus();
            return;
        }

        Intent intent = new Intent(this, PromptCreateKeywordActivity.class);
        intent.putExtra(EXTRA_RECIPIENT, name);
        intent.putExtra(EXTRA_ABOUT_ME, persona);
        intent.putExtra(EXTRA_GROUP,
                ((android.widget.EditText) findViewById(R.id.editGroup)).getText().toString().trim());
        startActivity(intent);
        // 1단계는 finish하지 않는다: 2단계에서 뒤로가면 입력값이 그대로 남아 있도록.
    }

    // 팝업은 슬라이드 대신 페이드 전환이 자연스럽다.
    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
