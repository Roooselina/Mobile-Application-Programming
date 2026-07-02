package com.example.aitonechanger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SearchActivity extends BaseActivity {

    private LinearLayout resultContainer;
    private EditText editSearch;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        applyInsetsToRoot();

        dbHelper = new DatabaseHelper(this);

        resultContainer = findViewById(R.id.resultContainer);
        editSearch = findViewById(R.id.editSearch);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        if (PromptData.currentUserId != -1) {
            PromptData.prompts = dbHelper.getPromptsByUserId(PromptData.currentUserId);
        }

        renderResults("");

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                String keyword = editSearch.getText().toString().trim();
                renderResults(keyword);
                return true;
            }
            return false;
        });
    }

    private void renderResults(String keyword) {
        resultContainer.removeAllViews();

        boolean hasResult = false;

        if (PromptData.currentUserId != -1) {
            PromptData.prompts = dbHelper.getPromptsByUserId(PromptData.currentUserId);
        }

        for (PromptData.Prompt prompt : PromptData.prompts) {
            String title = prompt.name;

            String lastMessage = dbHelper.getLastChatMessage(
                    PromptData.currentUserId,
                    prompt.id
            );

            String sub = shortenText(lastMessage, 30);
            String keywords = TextUtils.join(" ", prompt.keywords);

            if (keyword.isEmpty()
                    || containsIgnoreSpace(title, keyword)
                    || containsIgnoreSpace(lastMessage, keyword)
                    || containsIgnoreSpace(keywords, keyword)) {

                addResult(title, sub, prompt.id);
                hasResult = true;
            }
        }

        if (!hasResult) {
            showNoResult();
        }
    }

    private String shortenText(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "대화 내용이 없습니다.";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    private boolean containsIgnoreSpace(String text, String keyword) {
        if (text == null || keyword == null) return false;

        String cleanText = text.replace(" ", "").toLowerCase();
        String cleanKeyword = keyword.replace(" ", "").toLowerCase();

        return cleanText.contains(cleanKeyword);
    }

    private void showNoResult() {
        TextView noResult = new TextView(this);
        noResult.setText("검색 결과가 없습니다.");
        noResult.setTextSize(16);
        noResult.setTextColor(getColor(R.color.text_sub));
        noResult.setGravity(Gravity.CENTER);
        noResult.setPadding(0, dp(60), 0, 0);

        resultContainer.addView(noResult, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addResult(String title, String sub, String promptId) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(24), 0, dp(22), 0);
        card.setBackgroundResource(R.drawable.rounded_card);
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout textBox = new LinearLayout(this);
        textBox.setOrientation(LinearLayout.VERTICAL);
        textBox.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(19);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(getColor(R.color.text_main));
        titleView.setIncludeFontPadding(false);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);

        TextView subView = new TextView(this);
        subView.setText(sub);
        subView.setTextSize(15);
        subView.setTextColor(getColor(R.color.text_sub));
        subView.setIncludeFontPadding(true);
        subView.setSingleLine(true);
        subView.setEllipsize(TextUtils.TruncateAt.END);
        subView.setPadding(0, dp(12), 0, 0);

        textBox.addView(titleView, new LinearLayout.LayoutParams(-1, -2));
        textBox.addView(subView, new LinearLayout.LayoutParams(-1, -2));

        card.addView(textBox, new LinearLayout.LayoutParams(0, -1, 1));

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(34);
        arrow.setTypeface(null, Typeface.BOLD);
        arrow.setTextColor(getColor(R.color.text_main));
        arrow.setGravity(Gravity.CENTER);
        arrow.setIncludeFontPadding(false);

        card.addView(arrow, new LinearLayout.LayoutParams(dp(36), -1));

        card.setOnClickListener(v -> {
            PromptData.selectedPromptId = promptId;

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(104));
        lp.bottomMargin = dp(14);

        resultContainer.addView(card, lp);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}