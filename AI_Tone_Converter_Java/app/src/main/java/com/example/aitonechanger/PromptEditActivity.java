package com.example.aitonechanger;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

/**
 * 프롬프트 설정 보기/수정 (팝업).
 *
 * 생성할 때 입력한 모든 정보(대화 상대, 그룹, 나에 대한 정보,
 * 말투 키워드, 반드시 기억할 정보)를 한 화면에 보여주고 바로 수정한다.
 * 대화창 상단 제목을 눌렀을 때, 사이드바 메뉴의 "수정"에서 열린다.
 */
public class PromptEditActivity extends BaseActivity {

    private EditText editName;
    private EditText editGroup;
    private EditText editPersona;
    private EditText editMemory;
    private KeywordEditorHelper helper;
    private PromptData.Prompt prompt;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_prompt_edit);

        dbHelper = new DatabaseHelper(this);

        prompt = PromptData.getPrompt(PromptData.editingPromptId);

        if (prompt == null) {
            Toast.makeText(this, "수정할 프롬프트를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        editName = findViewById(R.id.editPromptName);
        editGroup = findViewById(R.id.editGroup);
        editPersona = findViewById(R.id.editPersona);
        editMemory = findViewById(R.id.editMemory);

        editName.setText(prompt.name);
        editGroup.setText(prompt.group);
        editPersona.setText(prompt.persona);
        editMemory.setText(prompt.memoryInfo);

        GridLayout grid = findViewById(R.id.keywordGrid);
        TextView count = findViewById(R.id.txtCount);

        helper = new KeywordEditorHelper(this, grid, count);
        helper.setSelected(new ArrayList<>(prompt.keywords));

        shrinkHints();

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnAddKeyword).setOnClickListener(v -> helper.showAddDialog());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
    }

    private void save() {
        String name = editName.getText().toString().trim();
        String group = editGroup.getText().toString().trim();
        String persona = editPersona.getText().toString().trim();
        String memory = editMemory.getText().toString().trim();
        ArrayList<String> selectedKeywords = helper.getSelected();

        if (name.isEmpty()) {
            Toast.makeText(this, "대화 상대를 입력해주세요.", Toast.LENGTH_SHORT).show();
            editName.requestFocus();
            return;
        }

        if (selectedKeywords != null && selectedKeywords.size() > PromptData.MAX_KEYWORD_COUNT) {
            Toast.makeText(this, "말투 키워드는 최대 25개까지만 선택할 수 있습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean result = dbHelper.updatePrompt(
                prompt.id,
                name,
                selectedKeywords == null ? new ArrayList<>() : selectedKeywords,
                persona,
                memory,
                group
        );

        if (!result) {
            Toast.makeText(this, "수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "수정되었습니다", Toast.LENGTH_SHORT).show();
        finish();
    }

    // 팝업은 페이드 전환
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
