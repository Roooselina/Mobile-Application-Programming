package com.example.aitonechanger;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * 새 프롬프트 생성 2단계 (팝업).
 *
 * 말투 키워드(최대 5개)와 반드시 기억할 정보를 입력받아 저장한 뒤,
 * 방금 생성한 프롬프트를 선택 상태로 만들어 해당 대화창으로 바로 이동한다.
 */
public class PromptCreateKeywordActivity extends BaseActivity {

    private EditText editMemory;
    private KeywordEditorHelper helper;
    private DatabaseHelper dbHelper;

    private String recipient;
    private String aboutMe;
    private String group;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_create_keywords);

        dbHelper = new DatabaseHelper(this);

        recipient = getIntent().getStringExtra(PromptCreateActivity.EXTRA_RECIPIENT);
        aboutMe = getIntent().getStringExtra(PromptCreateActivity.EXTRA_ABOUT_ME);
        group = getIntent().getStringExtra(PromptCreateActivity.EXTRA_GROUP);

        if (recipient == null || recipient.trim().isEmpty()) {
            Toast.makeText(this, "대화 상대 정보가 없습니다. 처음부터 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        TextView summary = findViewById(R.id.txtRecipientSummary);
        summary.setText("대화 상대: " + recipient);

        editMemory = findViewById(R.id.editMemory);
        GridLayout grid = findViewById(R.id.keywordGrid);
        TextView count = findViewById(R.id.txtCount);

        helper = new KeywordEditorHelper(this, grid, count);
        helper.setSelected(new ArrayList<>());

        shrinkHints();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddKeyword).setOnClickListener(v -> helper.showAddDialog());
        findViewById(R.id.btnCreate).setOnClickListener(v -> createPrompt());
    }

    private void createPrompt() {
        ArrayList<String> selectedKeywords = helper.getSelected();
        String memory = editMemory.getText().toString().trim();

        if (PromptData.currentUserId == -1) {
            Toast.makeText(this, "로그인 정보가 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedKeywords != null && selectedKeywords.size() > PromptData.MAX_KEYWORD_COUNT) {
            Toast.makeText(this, "말투 키워드는 최대 25개까지만 선택할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        long newId = dbHelper.insertPromptReturnId(
                PromptData.currentUserId,
                recipient,
                selectedKeywords == null ? new ArrayList<>() : selectedKeywords,
                aboutMe == null ? "" : aboutMe,
                memory,
                group == null ? "" : group
        );

        if (newId == -1) {
            Toast.makeText(this, "프롬프트 생성에 실패했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 방금 생성한 프롬프트를 선택 상태로 만들고 바로 대화창으로 이동
        PromptData.selectedPromptId = String.valueOf(newId);

        Toast.makeText(this, "프롬프트가 생성되었습니다", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
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
