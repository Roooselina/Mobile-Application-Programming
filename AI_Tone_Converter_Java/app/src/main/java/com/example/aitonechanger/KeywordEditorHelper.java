package com.example.aitonechanger;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class KeywordEditorHelper {

    private final Context context;
    private final GridLayout grid;
    private final TextView countView;
    private final ArrayList<String> allKeywords = new ArrayList<>();
    private final ArrayList<String> selected = new ArrayList<>();
    private final DatabaseHelper dbHelper;

    public KeywordEditorHelper(Context context, GridLayout grid, TextView countView) {
        this.context = context;
        this.grid = grid;
        this.countView = countView;
        this.dbHelper = new DatabaseHelper(context);

        if (PromptData.currentUserId != -1) {
            dbHelper.insertDefaultKeywordsIfNeeded(PromptData.currentUserId);
            allKeywords.addAll(dbHelper.getKeywordsByUserId(PromptData.currentUserId));
        }

        render();
    }

    public void setSelected(ArrayList<String> initial) {
        selected.clear();

        if (initial != null) {
            selected.addAll(initial);
        }

        render();
    }

    public ArrayList<String> getSelected() {
        return new ArrayList<>(selected);
    }

    public void showAddDialog() {
        EditText input = new EditText(context);
        android.text.SpannableString hint = new android.text.SpannableString("예) 담백함, 귀여움, 차갑게");
        hint.setSpan(new android.text.style.AbsoluteSizeSpan(12, true),
                0, hint.length(), android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        input.setHint(hint);
        input.setSingleLine(true);

        new AlertDialog.Builder(context)
                .setTitle("직접 키워드 추가")
                .setView(input)
                .setNegativeButton("취소", null)
                .setPositiveButton("추가", (d, w) -> {
                    String word = input.getText().toString().trim();

                    if (word.isEmpty()) {
                        Toast.makeText(context, "키워드를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (allKeywords.contains(word)) {
                        Toast.makeText(context, "이미 존재하는 키워드입니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (allKeywords.size() >= PromptData.MAX_KEYWORD_COUNT) {
                        Toast.makeText(context, "키워드는 최대 25개까지만 만들 수 있습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean result = dbHelper.insertKeyword(PromptData.currentUserId, word);

                    if (!result) {
                        Toast.makeText(context, "키워드 추가에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    allKeywords.add(word);
                    render();
                })
                .show();
    }

    private void showEditDeleteDialog(String oldWord) {
        EditText input = new EditText(context);
        input.setText(oldWord);
        input.setSingleLine(true);
        input.setSelection(input.getText().length());

        new AlertDialog.Builder(context)
                .setTitle("키워드 수정 / 삭제")
                .setView(input)
                .setNegativeButton("삭제", (dialog, which) -> {
                    boolean result = dbHelper.deleteKeyword(PromptData.currentUserId, oldWord);

                    if (!result) {
                        Toast.makeText(context, "키워드 삭제에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    allKeywords.remove(oldWord);
                    selected.remove(oldWord);

                    render();
                    Toast.makeText(context, "키워드가 삭제되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("취소", null)
                .setPositiveButton("수정", (dialog, which) -> {
                    String newWord = input.getText().toString().trim();

                    if (newWord.isEmpty()) {
                        Toast.makeText(context, "키워드를 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!newWord.equals(oldWord) && allKeywords.contains(newWord)) {
                        Toast.makeText(context, "이미 존재하는 키워드입니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    boolean result = dbHelper.updateKeyword(
                            PromptData.currentUserId,
                            oldWord,
                            newWord
                    );

                    if (!result) {
                        Toast.makeText(context, "키워드 수정에 실패했습니다.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int index = allKeywords.indexOf(oldWord);

                    if (index != -1) {
                        allKeywords.set(index, newWord);
                    }

                    if (selected.contains(oldWord)) {
                        selected.remove(oldWord);
                        selected.add(newWord);
                    }

                    render();
                    Toast.makeText(context, "키워드가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void render() {
        grid.removeAllViews();

        for (String word : allKeywords) {
            TextView chip = new TextView(context);

            boolean checked = selected.contains(word);

            chip.setText(word + (checked ? "  ×" : ""));
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(7), 0, dp(7), 0);
            chip.setTextColor(checked ? context.getColor(R.color.blue) : context.getColor(R.color.text_main));
            chip.setBackgroundResource(checked ? R.drawable.chip_selected : R.drawable.chip_unselected);

            chip.setOnClickListener(v -> toggle(word));

            chip.setOnLongClickListener(v -> {
                showEditDeleteDialog(word);
                return true;
            });

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 0;
            lp.height = dp(38);
            lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            lp.setMargins(dp(4), dp(5), dp(4), dp(5));

            grid.addView(chip, lp);
        }

        updateCount();
    }

    private void toggle(String word) {
        if (selected.contains(word)) {
            selected.remove(word);
        } else {
            if (selected.size() >= PromptData.MAX_SELECTED_KEYWORD_COUNT) {
                Toast.makeText(context, "말투 키워드는 최대 5개까지 선택할 수 있습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            selected.add(word);
        }

        render();
    }

    private void updateCount() {
        countView.setText(selected.size() + "/" + PromptData.MAX_SELECTED_KEYWORD_COUNT);
    }

    private int dp(int v) {
        return (int) (v * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}