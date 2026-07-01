package com.example.aitonechanger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    private FrameLayout rootFrame;
    private LinearLayout chatContainer;
    private LinearLayout promptListContainer;
    private View sidebar;
    private View sidebarDim;
    private TextView headerTitle;
    private EditText editMessage;
    private ScrollView chatScroll;

    private boolean hasSentInThisScreen = false;
    private float touchDownX;
    private float touchDownY;

    private DatabaseHelper dbHelper;
    private com.example.aitonechanger.data.ToneChatRepository toneRepo;

    // 실시간 미리보기 상태
    private View previewBox;
    private TextView previewText;
    private final android.os.Handler previewHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable previewRunnable;
    private String previewInput;    // 미리보기를 만든 입력
    private String previewResult;   // 미리보기 결과 (같은 입력으로 전송 시 재사용)

    // 입력 드래프트 자동 저장
    private String draftPromptId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        applyInsetsToRoot();
        shrinkHints();

        dbHelper = new DatabaseHelper(this);
        toneRepo = new com.example.aitonechanger.data.ToneChatRepository();

        rootFrame = findViewById(R.id.rootFrame);
        chatContainer = findViewById(R.id.chatContainer);
        promptListContainer = findViewById(R.id.promptListContainer);
        sidebar = findViewById(R.id.sidebar);
        sidebarDim = findViewById(R.id.sidebarDim);
        headerTitle = findViewById(R.id.txtHeaderTitle);
        editMessage = findViewById(R.id.editMessage);
        chatScroll = findViewById(R.id.chatScroll);

        findViewById(R.id.btnMenu).setOnClickListener(v -> openSidebar());
        findViewById(R.id.btnCloseSidebar).setOnClickListener(v -> closeSidebar());
        sidebarDim.setOnClickListener(v -> closeSidebar());

        findViewById(R.id.sidebarSearch).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class))
        );

        findViewById(R.id.btnSearch).setOnClickListener(v ->
                startActivity(new Intent(this, SearchActivity.class))
        );

        findViewById(R.id.btnProfile).setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class))
        );

        findViewById(R.id.btnNewPrompt).setOnClickListener(v ->
                startActivity(new Intent(this, PromptCreateActivity.class))
        );

        findViewById(R.id.btnSend).setOnClickListener(v -> sendMessage());

        // 최근 입력 히스토리 (같은 문장 다시 안 쳐도 됨)
        findViewById(R.id.btnInputHistory).setOnClickListener(v -> showInputHistory());

        // 실시간 변환 미리보기 (설정에서 켰을 때만): 입력 멈춘 뒤 1.2초 후 자동 변환
        previewBox = findViewById(R.id.previewBox);
        previewText = findViewById(R.id.previewText);
        editMessage.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void onTextChanged(CharSequence cs, int a, int b, int c) {
                hidePreviewIfStale();
                schedulePreview();
            }

            @Override
            public void beforeTextChanged(CharSequence cs, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable e) {
            }
        });

        renderAll();
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (previewRunnable != null) {
            previewHandler.removeCallbacks(previewRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 입력하던 내용 자동 저장 (앱을 꺼도 복원)
        if (PromptData.selectedPromptId != null && editMessage != null) {
            AppPrefs.setDraft(this, PromptData.selectedPromptId,
                    editMessage.getText().toString());
        }
    }

    private void loadUserPromptsFromDb() {
        if (PromptData.currentUserId != -1) {
            PromptData.prompts = dbHelper.getPromptsByUserId(PromptData.currentUserId);
        }
    }

    private void renderAll() {
        loadUserPromptsFromDb();
        renderPromptList();
        renderChatState();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                break;

            case MotionEvent.ACTION_UP:
                float dx = event.getX() - touchDownX;
                float dy = Math.abs(event.getY() - touchDownY);

                if (dy < dp(80)) {
                    if (touchDownX < dp(32) && dx > dp(90)) {
                        openSidebar();
                        return true;
                    }

                    if (sidebar != null && sidebar.getVisibility() == View.VISIBLE && dx < -dp(90)) {
                        closeSidebar();
                        return true;
                    }
                }
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    private void openSidebar() {
        renderPromptList();

        sidebarDim.setVisibility(View.VISIBLE);
        sidebar.setVisibility(View.VISIBLE);
        sidebar.setTranslationX(-dp(318));
        sidebar.animate().translationX(0).setDuration(180).start();
    }

    private void closeSidebar() {
        sidebar.animate()
                .translationX(-dp(318))
                .setDuration(160)
                .withEndAction(() -> {
                    sidebar.setVisibility(View.GONE);
                    sidebarDim.setVisibility(View.GONE);
                    sidebar.setTranslationX(0);
                })
                .start();
    }

    private void renderPromptList() {
        loadUserPromptsFromDb();

        promptListContainer.removeAllViews();

        addSectionTitle(R.drawable.ic_star_blue, "즐겨찾기", true);

        for (PromptData.Prompt p : PromptData.prompts) {
            if (p.favorite) {
                addPromptRow(p, true);
            }
        }

        addSpaceTo(promptListContainer, dp(14));

        // 그룹(폴더)별 섹션: 직장/학교/가족처럼 묶어서 표시
        java.util.LinkedHashSet<String> groups = new java.util.LinkedHashSet<>();
        for (PromptData.Prompt p : PromptData.prompts) {
            if (!p.favorite && p.group != null && !p.group.isEmpty()) {
                groups.add(p.group);
            }
        }

        for (String g : groups) {
            addSectionTitle(R.drawable.ic_message_square, g, false);
            for (PromptData.Prompt p : PromptData.prompts) {
                if (!p.favorite && g.equals(p.group)) {
                    addPromptRow(p, false);
                }
            }
            addSpaceTo(promptListContainer, dp(10));
        }

        // 그룹 없는 프롬프트
        addSectionTitle(R.drawable.ic_clock, "최근", false);

        for (PromptData.Prompt p : PromptData.prompts) {
            if (!p.favorite && (p.group == null || p.group.isEmpty())) {
                addPromptRow(p, false);
            }
        }

        TextView more = new TextView(this);
        more.setText("더보기");
        more.setTextColor(getColor(R.color.text_sub));
        more.setTextSize(14);
        more.setGravity(Gravity.CENTER_VERTICAL);
        more.setPadding(dp(6), 0, dp(6), 0);

        promptListContainer.addView(more, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private void addSectionTitle(int iconRes, String text, boolean blueIcon) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), 0, dp(6), 0);

        FrameLayout iconSlot = new FrameLayout(this);
        LinearLayout.LayoutParams iconSlotLp =
                new LinearLayout.LayoutParams(dp(22), dp(42));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);

        FrameLayout.LayoutParams iconLp =
                new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);

        iconSlot.addView(icon, iconLp);
        row.addView(iconSlot, iconSlotLp);

        TextView title = new TextView(this);
        title.setText(text);
        title.setTextColor(getColor(R.color.text_sub));
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setIncludeFontPadding(false);

        LinearLayout.LayoutParams tlp =
                new LinearLayout.LayoutParams(0, dp(42), 1);

        tlp.leftMargin = dp(10);
        row.addView(title, tlp);

        promptListContainer.addView(row, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    private void addPromptRow(PromptData.Prompt p, boolean favoriteSection) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), 0, dp(6), 0);

        row.setBackgroundResource(p.id.equals(PromptData.selectedPromptId)
                ? R.drawable.selected_row
                : R.drawable.row_sidebar_ripple);

        row.setClickable(true);
        row.setFocusable(true);

        FrameLayout iconSlot = new FrameLayout(this);
        LinearLayout.LayoutParams iconSlotLp =
                new LinearLayout.LayoutParams(dp(22), dp(46));

        if (favoriteSection) {
            ImageView star = new ImageView(this);
            star.setImageResource(R.drawable.ic_star_blue);

            FrameLayout.LayoutParams starLp =
                    new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER);

            iconSlot.addView(star, starLp);
        }

        row.addView(iconSlot, iconSlotLp);

        TextView name = new TextView(this);
        name.setText(p.name);
        name.setTextSize(17);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setIncludeFontPadding(false);

        name.setTextColor(p.id.equals(PromptData.selectedPromptId)
                ? getColor(R.color.blue)
                : getColor(R.color.text_main));

        name.setTypeface(null,
                p.id.equals(PromptData.selectedPromptId)
                        ? Typeface.BOLD
                        : Typeface.NORMAL);

        LinearLayout.LayoutParams nameLp =
                new LinearLayout.LayoutParams(0, dp(46), 1);

        nameLp.leftMargin = dp(10);
        row.addView(name, nameLp);

        ImageButton menu = new ImageButton(this);
        menu.setImageResource(R.drawable.ic_more_vertical);
        menu.setBackgroundResource(android.R.color.transparent);
        menu.setPadding(dp(10), dp(10), dp(10), dp(10));
        menu.setScaleType(ImageView.ScaleType.CENTER);

        row.addView(menu, new LinearLayout.LayoutParams(dp(42), dp(46)));

        row.setOnClickListener(v -> {
            PromptData.selectedPromptId = p.id;
            hasSentInThisScreen = false;
            closeSidebar();
            renderChatState();
            renderPromptList();
        });

        menu.setOnClickListener(v -> showPromptMenu(menu, p));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
        lp.bottomMargin = dp(6);

        promptListContainer.addView(row, lp);
    }

    private void showPromptMenu(View anchor, PromptData.Prompt p) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        box.setBackgroundResource(R.drawable.rounded_menu);

        final PopupWindow popup = new PopupWindow(box, dp(180), dp(154), true);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(dp(8));

        box.addView(menuItem(
                p.favorite ? R.drawable.ic_star_outline : R.drawable.ic_star_blue,
                p.favorite ? "즐겨찾기 해제" : "즐겨찾기 추가",
                getColor(R.color.text_main),
                v -> {
                    popup.dismiss();
                    p.favorite = !p.favorite;

                    dbHelper.updateFavorite(p.id, p.favorite);

                    Toast.makeText(
                            this,
                            p.favorite ? "즐겨찾기에 추가되었습니다" : "즐겨찾기에서 해제되었습니다",
                            Toast.LENGTH_SHORT
                    ).show();

                    renderAll();
                }
        ));

        box.addView(menuItem(
                R.drawable.ic_pencil,
                "수정",
                getColor(R.color.text_main),
                v -> {
                    popup.dismiss();
                    PromptData.editingPromptId = p.id;
                    startActivity(new Intent(this, PromptEditActivity.class));
                }
        ));

        box.addView(menuItem(
                R.drawable.ic_trash,
                "삭제",
                getColor(R.color.danger),
                v -> {
                    popup.dismiss();
                    dbHelper.deletePrompt(p.id);

                    if (p.id.equals(PromptData.selectedPromptId)) {
                        PromptData.selectedPromptId = null;
                    }

                    Toast.makeText(this, "삭제되었습니다", Toast.LENGTH_SHORT).show();
                    renderAll();
                }
        ));

        popup.showAsDropDown(anchor, -dp(158), -dp(8));
    }

    private View menuItem(int iconRes, String text, int color, View.OnClickListener action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), 0, dp(12), 0);
        row.setBackgroundResource(R.drawable.row_profile_ripple);
        row.setOnClickListener(v -> action.onClick(v));
        row.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(46)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(16);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.leftMargin = dp(12);

        row.addView(tv, lp);

        return row;
    }

    private void renderChatState() {
        chatContainer.removeAllViews();

        PromptData.Prompt selected = PromptData.getSelectedPrompt();

        headerTitle.setText(selected == null
                ? "AI 오늘의 말투 변환기"
                : selected.name);

        // 대화가 선택된 상태에서는 제목 옆에 연필 아이콘을 보여주고,
        // 제목을 누르면 이 대화의 프롬프트 설정 전체(대화 상대/그룹/나에 대한 정보/키워드/기억 정보)를
        // 팝업에서 한눈에 보고 바로 수정할 수 있다.
        if (selected != null) {
            headerTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_pencil, 0);
            headerTitle.setCompoundDrawablePadding(dp(6));
            headerTitle.setOnClickListener(v -> {
                PromptData.editingPromptId = selected.id;
                startActivity(new Intent(this, PromptEditActivity.class));
            });
        } else {
            headerTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            headerTitle.setOnClickListener(null);
            headerTitle.setClickable(false);
        }

        // 프롬프트가 바뀌면: 이전 프롬프트의 입력은 드래프트로 저장, 새 프롬프트의 드래프트 복원
        String newId = selected == null ? null : selected.id;
        if ((draftPromptId == null && newId != null)
                || (draftPromptId != null && !draftPromptId.equals(newId))) {
            if (draftPromptId != null) {
                AppPrefs.setDraft(this, draftPromptId, editMessage.getText().toString());
            }
            editMessage.setText(newId == null ? "" : AppPrefs.getDraft(this, newId));
            if (editMessage.getText().length() > 0) {
                editMessage.setSelection(editMessage.getText().length());
            }
            draftPromptId = newId;
            hidePreview();
        }

        if (selected == null) {
            addCenteredState(
                    R.drawable.ic_message_square,
                    "아직 선택된 프롬프트가 없습니다.",
                    "왼쪽 사이드바에서 프롬프트를 선택하거나\n새 프롬프트를 생성해보세요.",
                    true
            );
            return;
        }

        ArrayList<PromptData.ChatMessage> messages =
                dbHelper.getChatMessages(PromptData.currentUserId, selected.id);

        if (messages.isEmpty()) {
            hasSentInThisScreen = false;
            addReadyState(selected);
            return;
        }

        hasSentInThisScreen = true;
        addTodayChip();

        String lastUserText = "";
        for (PromptData.ChatMessage msg : messages) {
            if ("user".equals(msg.sender)) {
                addUserMessage(msg.message, msg.timeText);
                lastUserText = msg.message;
            } else {
                addAiMessage(msg, lastUserText);
            }
        }

        chatScroll.postDelayed(() ->
                chatScroll.fullScroll(View.FOCUS_DOWN), 150);
    }

    private void addCenteredState(int iconRes, String title, String sub, boolean showButton) {
        SpaceTop(dp(130));

        chatContainer.addView(centerIcon(iconRes));

        TextView t = centerText(title, 16, true, getColor(R.color.text_main));
        t.setPadding(0, dp(18), 0, 0);
        chatContainer.addView(t);

        TextView s = centerText(sub, 14, false, getColor(R.color.text_sub));
        s.setPadding(0, dp(10), 0, 0);
        chatContainer.addView(s);

        if (showButton) {
            TextView btn = new TextView(this);
            btn.setText("+ 새 프롬프트 생성");
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(15);
            btn.setTypeface(null, Typeface.BOLD);
            btn.setGravity(Gravity.CENTER);
            btn.setBackgroundResource(R.drawable.rounded_primary);

            btn.setOnClickListener(v ->
                    startActivity(new Intent(this, PromptCreateActivity.class))
            );

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(50));
            lp.setMargins(dp(22), dp(24), dp(22), 0);

            chatContainer.addView(btn, lp);
        }
    }

    private View centerIcon(int iconRes) {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundResource(R.drawable.rounded_icon_blue_light);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);

        FrameLayout.LayoutParams ilp =
                new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER);

        box.addView(icon, ilp);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(64), dp(64));

        lp.gravity = Gravity.CENTER_HORIZONTAL;
        box.setLayoutParams(lp);

        return box;
    }

    private void addReadyState(PromptData.Prompt p) {
        SpaceTop(dp(125));

        chatContainer.addView(centerIcon(R.drawable.ic_check_blue));

        TextView t = centerText(
                p.name + " 프롬프트가 준비되었습니다.",
                16,
                true,
                getColor(R.color.text_main)
        );

        t.setPadding(0, dp(18), 0, 0);
        chatContainer.addView(t);

        TextView sub = centerText(
                "보내고 싶은 문장을 입력하면\n선택한 말투 키워드에 맞게 AI가 바꿔드립니다.",
                14,
                false,
                getColor(R.color.text_sub)
        );

        sub.setPadding(0, dp(10), 0, 0);
        chatContainer.addView(sub);

        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER);
        chips.setPadding(0, dp(18), 0, 0);

        for (String k : p.keywords) {
            TextView chip = new TextView(this);
            chip.setText("#" + k);
            chip.setTextSize(12);
            chip.setTextColor(getColor(R.color.blue));
            chip.setBackgroundResource(R.drawable.chip_selected);
            chip.setGravity(Gravity.CENTER);

            LinearLayout.LayoutParams clp =
                    new LinearLayout.LayoutParams(-2, dp(30));

            clp.rightMargin = dp(6);
            chip.setPadding(dp(9), 0, dp(9), 0);

            chips.addView(chip, clp);
        }

        chatContainer.addView(chips, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView centerText(String text, int sp, boolean bold, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);

        if (bold) {
            tv.setTypeface(null, Typeface.BOLD);
        }

        tv.setLineSpacing(dp(3), 1.0f);
        tv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        return tv;
    }

    private void sendMessage() {
        PromptData.Prompt selected = PromptData.getSelectedPrompt();
        String text = editMessage.getText().toString().trim();

        if (selected == null) {
            Toast.makeText(this, "먼저 프롬프트를 선택해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        if (text.isEmpty()) {
            return;
        }

        // 이번 입력 이전까지의 대화 이력 (API 컨텍스트로 전달)
        ArrayList<PromptData.ChatMessage> priorHistory =
                dbHelper.getChatMessages(PromptData.currentUserId, selected.id);

        String userTime = getCurrentTimeText();

        if (!hasSentInThisScreen) {
            chatContainer.removeAllViews();
            addTodayChip();
            hasSentInThisScreen = true;
        }

        addUserMessage(text, userTime);

        dbHelper.insertChatMessage(
                PromptData.currentUserId,
                selected.id,
                "user",
                text,
                userTime
        );

        editMessage.setText("");
        AppPrefs.setDraft(this, selected.id, ""); // 전송했으니 드래프트 비움

        // 실시간 미리보기로 이미 변환된 입력이면 API 호출 없이 즉시 사용
        if (previewResult != null && text.equals(previewInput)) {
            String cached = previewResult;
            hidePreview();
            deliverAiResponse(selected, text, cached, null);
            return;
        }
        hidePreview();

        final View typing = addTypingIndicator();
        chatScroll.postDelayed(() -> chatScroll.fullScroll(View.FOCUS_DOWN), 120);

        final PromptData.Prompt promptForCall = selected;
        final String userText = text;
        toneRepo.correctTone(
                this,
                selected.name,
                selected.keywords,
                selected.persona,
                selected.memoryInfo,
                priorHistory,
                text,
                new com.example.aitonechanger.data.ToneChatRepository.CorrectionCallback() {
                    @Override
                    public void onSuccess(String correctedText) {
                        deliverAiResponse(promptForCall, userText, correctedText, typing);
                    }

                    @Override
                    public void onError(String errorMessage) {
                        if (typing != null) {
                            chatContainer.removeView(typing);
                        }
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                }
        );
    }

    /**
     * 변환 결과 처리: 항상 DB에 먼저 저장하고,
     * 사용자가 다른 프롬프트로 이동해 있어도 결과가 유실되지 않게 한다. (백그라운드 요청)
     */
    private void deliverAiResponse(PromptData.Prompt prompt, String userText,
                                   String correctedText, View typing) {
        if (isFinishing() || isDestroyed()) {
            // 화면이 닫혔어도 결과는 저장한다 (유실 방지)
            dbHelper.insertChatMessageReturnId(PromptData.currentUserId,
                    prompt.id, "ai", correctedText, getCurrentTimeText());
            return;
        }

        if (typing != null) {
            chatContainer.removeView(typing);
        }

        String aiTime = getCurrentTimeText();

        long msgId = dbHelper.insertChatMessageReturnId(
                PromptData.currentUserId,
                prompt.id,
                "ai",
                correctedText,
                aiTime
        );

        if (prompt.id.equals(PromptData.selectedPromptId)) {
            PromptData.ChatMessage aiMsg =
                    new PromptData.ChatMessage("ai", correctedText, aiTime);
            aiMsg.id = msgId;
            addAiMessage(aiMsg, userText);
            chatScroll.postDelayed(() ->
                    chatScroll.fullScroll(View.FOCUS_DOWN), 120);
        } else {
            // 다른 대화로 이동한 상태: 저장은 끝났으니 알림만
            Toast.makeText(this,
                    "[" + prompt.name + "] 변환이 완료되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    // ----- 실시간 변환 미리보기 -----

    private void schedulePreview() {
        if (previewRunnable != null) {
            previewHandler.removeCallbacks(previewRunnable);
        }

        if (!AppPrefs.isLivePreview(this)) return;

        final PromptData.Prompt selected = PromptData.getSelectedPrompt();
        final String text = editMessage.getText().toString().trim();
        if (selected == null || text.length() < 2) return;

        previewRunnable = () -> {
            final String input = editMessage.getText().toString().trim();
            if (input.length() < 2) return;

            ArrayList<PromptData.ChatMessage> history =
                    dbHelper.getChatMessages(PromptData.currentUserId, selected.id);

            toneRepo.correctTone(
                    MainActivity.this,
                    selected.name, selected.keywords, selected.persona, selected.memoryInfo,
                    history, input,
                    new com.example.aitonechanger.data.ToneChatRepository.CorrectionCallback() {
                        @Override
                        public void onSuccess(String correctedText) {
                            if (isFinishing() || isDestroyed()) return;
                            // 입력이 그대로일 때만 미리보기 표시
                            if (input.equals(editMessage.getText().toString().trim())
                                    && selected.id.equals(PromptData.selectedPromptId)) {
                                previewInput = input;
                                previewResult = correctedText;
                                previewText.setText(correctedText);
                                previewBox.setVisibility(View.VISIBLE);
                            }
                        }

                        @Override
                        public void onError(String errorMessage) {
                            // 미리보기 실패는 조용히 무시 (전송 시 정식 호출됨)
                        }
                    });
        };
        previewHandler.postDelayed(previewRunnable, 1200);
    }

    private void hidePreview() {
        previewInput = null;
        previewResult = null;
        if (previewBox != null) previewBox.setVisibility(View.GONE);
    }

    /** 입력이 바뀌어 미리보기가 더 이상 맞지 않으면 숨긴다. */
    private void hidePreviewIfStale() {
        if (previewBox == null || previewBox.getVisibility() != View.VISIBLE) return;
        String now = editMessage.getText().toString().trim();
        if (previewInput == null || !previewInput.equals(now)) {
            previewBox.setVisibility(View.GONE);
        }
    }

    // ----- 최근 입력 히스토리 -----

    private void showInputHistory() {
        PromptData.Prompt selected = PromptData.getSelectedPrompt();
        if (selected == null) {
            Toast.makeText(this, "먼저 프롬프트를 선택해주세요", Toast.LENGTH_SHORT).show();
            return;
        }

        final ArrayList<String> recents =
                dbHelper.getRecentUserInputs(PromptData.currentUserId, selected.id, 10);

        if (recents.isEmpty()) {
            Toast.makeText(this, "최근 입력 기록이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 목록에는 앞부분만 보여준다
        String[] labels = new String[recents.size()];
        for (int i = 0; i < recents.size(); i++) {
            String t = recents.get(i);
            labels[i] = t.length() > 40 ? t.substring(0, 40) + "…" : t;
        }

        new AlertDialog.Builder(this)
                .setTitle("최근 입력")
                .setItems(labels, (d, which) -> {
                    editMessage.setText(recents.get(which));
                    editMessage.setSelection(editMessage.getText().length());
                })
                .setNegativeButton("닫기", null)
                .show();
    }

    /**
     * Shows a lightweight "..." bubble while the correction is in flight.
     * Returns the view so it can be removed when the response arrives.
     */
    private View addTypingIndicator() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.LEFT | Gravity.BOTTOM);

        TextView msg = new TextView(this);
        msg.setText("\u00b7\u00b7\u00b7");
        msg.setTextSize(16);
        msg.setTextColor(getColor(R.color.text_sub));
        msg.setPadding(dp(18), dp(14), dp(18), dp(14));
        msg.setBackgroundResource(R.drawable.chat_ai);

        line.addView(msg, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.rightMargin = dp(26);
        lp.topMargin = dp(10);

        chatContainer.addView(line, lp);
        return line;
    }

    private void addTodayChip() {
        TextView chip = new TextView(this);
        chip.setText("오늘");
        chip.setTextSize(13);
        chip.setTextColor(getColor(R.color.text_sub));
        chip.setGravity(Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.chip_unselected);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(58), dp(34));

        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = dp(24);

        chatContainer.addView(chip, lp);
    }

    private void addUserMessage(String text, String time) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.RIGHT | Gravity.BOTTOM);

        TextView timeView = new TextView(this);
        timeView.setText(time);
        timeView.setTextSize(11);
        timeView.setTextColor(getColor(R.color.text_sub));

        TextView msg = new TextView(this);
        msg.setText(text);
        msg.setTextSize(16);
        msg.setTextColor(getColor(R.color.text_main));
        msg.setPadding(dp(18), dp(12), dp(18), dp(12));
        msg.setBackgroundResource(R.drawable.chat_user);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.rightMargin = dp(8);

        line.addView(timeView, tlp);
        line.addView(msg, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);

        lp.leftMargin = dp(54);
        lp.bottomMargin = dp(12);

        chatContainer.addView(line, lp);
    }

    /** 긴 답변은 8줄까지만 보여주고 "더보기"로 펼친다. */
    private static final int COLLAPSE_LINES = 8;
    private static final int COLLAPSE_CHARS = 300;

    private void addAiMessage(PromptData.ChatMessage aiMsg, String originalUserText) {
        final String text = aiMsg.displayText();

        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.LEFT | Gravity.BOTTOM);

        final TextView msg = new TextView(this);
        msg.setText(text);
        msg.setTextSize(16);
        msg.setLineSpacing(dp(4), 1.0f);
        msg.setTextColor(getColor(R.color.text_main));
        msg.setPadding(dp(18), dp(16), dp(18), dp(16));
        msg.setBackgroundResource(R.drawable.chat_ai);

        // 제스처: 오른쪽→왼쪽으로 밀면 새 답변(다른 버전) 생성,
        //         꾹 누르면 답변을 직접 수정 (API 호출 없이 수정본 저장)
        final android.view.GestureDetector gd = new android.view.GestureDetector(this,
                new android.view.GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public void onLongPress(MotionEvent e) {
                        showManualEditDialog(aiMsg);
                    }

                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2,
                                           float velocityX, float velocityY) {
                        if (e1 == null || e2 == null) return false;
                        float dx = e2.getX() - e1.getX();
                        float dyAbs = Math.abs(e2.getY() - e1.getY());
                        if (dx < -dp(70) && dyAbs < dp(60) && velocityX < -600) {
                            regenerateMessage(aiMsg, originalUserText, null);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean onDown(MotionEvent e) {
                        return true;
                    }
                });
        msg.setOnTouchListener((v, event) -> gd.onTouchEvent(event));

        // 긴 결과 접기
        final boolean longText = text.length() > COLLAPSE_CHARS;
        if (longText) {
            msg.setMaxLines(COLLAPSE_LINES);
            msg.setEllipsize(TextUtils.TruncateAt.END);
        }

        line.addView(msg, new LinearLayout.LayoutParams(0, -2, 1));

        TextView timeView = new TextView(this);
        timeView.setText(aiMsg.timeText);
        timeView.setTextSize(11);
        timeView.setTextColor(getColor(R.color.text_sub));

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.leftMargin = dp(8);
        line.addView(timeView, tlp);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.rightMargin = dp(26);
        lp.topMargin = dp(4);
        chatContainer.addView(line, lp);

        // ----- 액션 줄 (미니멀): ◀ n/m ▶, 재생성, 좋아요/별로예요, 복사, 공유, 더보기 -----
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        scroll.addView(actions, new FrameLayout.LayoutParams(-2, -1));

        // 버전 이동 (여러 버전이 있을 때만)
        if (aiMsg.versionCount() > 1) {
            final TextView counter = new TextView(this);
            counter.setText((aiMsg.currentVersion + 1) + "/" + aiMsg.versionCount());
            counter.setTextSize(12);
            counter.setTextColor(getColor(R.color.text_sub));
            counter.setGravity(Gravity.CENTER);

            TextView prev = smallAction("◀", v -> {
                if (aiMsg.currentVersion > 0) {
                    aiMsg.currentVersion--;
                    dbHelper.setCurrentVersion(aiMsg.id, aiMsg.currentVersion);
                    msg.setText(aiMsg.displayText());
                    counter.setText((aiMsg.currentVersion + 1) + "/" + aiMsg.versionCount());
                }
            });
            TextView next = smallAction("▶", v -> {
                if (aiMsg.currentVersion < aiMsg.versionCount() - 1) {
                    aiMsg.currentVersion++;
                    dbHelper.setCurrentVersion(aiMsg.id, aiMsg.currentVersion);
                    msg.setText(aiMsg.displayText());
                    counter.setText((aiMsg.currentVersion + 1) + "/" + aiMsg.versionCount());
                }
            });

            actions.addView(prev);
            LinearLayout.LayoutParams cnt = new LinearLayout.LayoutParams(-2, -1);
            cnt.rightMargin = dp(4);
            cnt.leftMargin = dp(4);
            actions.addView(counter, cnt);
            actions.addView(next);
        }

        // 다른 버전 생성 (원형 화살표 + 반짝이 아이콘)
        actions.addView(smallIconAction(R.drawable.ic_regenerate, "다른 버전 생성",
                v -> regenerateMessage(aiMsg, originalUserText, null)));

        // 피드백 (라인 아이콘, 싫어요는 좋아요 아이콘 180도 회전)
        final ImageView up = smallIconAction(R.drawable.ic_thumb_up, "좋아요", null);
        final ImageView down = smallIconAction(R.drawable.ic_thumb_up, "별로예요", null);
        down.setRotation(180f);
        applyFeedbackStyle(up, down, aiMsg.feedback);

        up.setOnClickListener(v -> {
            aiMsg.feedback = aiMsg.feedback == 1 ? 0 : 1;
            dbHelper.setMessageFeedback(aiMsg.id, aiMsg.feedback);
            applyFeedbackStyle(up, down, aiMsg.feedback);
            if (aiMsg.feedback == 1) {
                Toast.makeText(this, "피드백이 저장되었습니다.", Toast.LENGTH_SHORT).show();
            }
        });
        down.setOnClickListener(v -> {
            aiMsg.feedback = aiMsg.feedback == -1 ? 0 : -1;
            dbHelper.setMessageFeedback(aiMsg.id, aiMsg.feedback);
            applyFeedbackStyle(up, down, aiMsg.feedback);
            if (aiMsg.feedback == -1) {
                new AlertDialog.Builder(this)
                        .setMessage("아쉬운 점을 반영해 다른 버전을 만들어볼까요?")
                        .setNegativeButton("아니요", null)
                        .setPositiveButton("다른 버전 생성", (d, w) ->
                                regenerateMessage(aiMsg, originalUserText,
                                        "이전 답변이 마음에 들지 않았다. 표현 방식을 바꿔서 다시 작성해줘."))
                        .show();
            }
        });
        actions.addView(up);
        actions.addView(down);

        actions.addView(smallIconAction(R.drawable.ic_copy, "복사",
                v -> copyText(msg.getText().toString())));
        actions.addView(smallIconAction(R.drawable.ic_share, "공유",
                v -> shareText(msg.getText().toString())));

        if (longText) {
            final TextView more = smallAction("더보기", null);
            more.setOnClickListener(v -> {
                if (msg.getMaxLines() == COLLAPSE_LINES) {
                    msg.setMaxLines(Integer.MAX_VALUE);
                    msg.setEllipsize(null);
                    more.setText("접기");
                } else {
                    msg.setMaxLines(COLLAPSE_LINES);
                    msg.setEllipsize(TextUtils.TruncateAt.END);
                    more.setText("더보기");
                }
            });
            actions.addView(more);
        }

        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(36));
        ap.topMargin = dp(4);
        ap.leftMargin = dp(4);
        chatContainer.addView(scroll, ap);
    }

    /** 테두리 없는 둥근 리플 배경 (액션 줄을 미니멀하게) */
    private int borderlessRipple() {
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(
                android.R.attr.selectableItemBackgroundBorderless, tv, true);
        return tv.resourceId;
    }

    /** 아이콘만 있는 미니멀 액션 버튼 */
    private ImageView smallIconAction(int iconRes, String contentDesc, View.OnClickListener listener) {
        ImageView b = new ImageView(this);
        b.setImageResource(iconRes);
        b.setContentDescription(contentDesc);
        b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        b.setBackgroundResource(borderlessRipple());
        b.setColorFilter(getColor(R.color.text_sub));
        b.setPadding(dp(7), dp(7), dp(7), dp(7));
        if (listener != null) b.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
        lp.rightMargin = dp(6);
        b.setLayoutParams(lp);
        return b;
    }

    /** 텍스트 미니멀 액션 버튼 (버전 이동 ◀ ▶, 더보기 등) */
    private TextView smallAction(String label, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTextColor(getColor(R.color.text_sub));
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(borderlessRipple());
        b.setPadding(dp(8), 0, dp(8), 0);
        if (listener != null) b.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
        lp.rightMargin = dp(4);
        b.setLayoutParams(lp);
        return b;
    }

    private void applyFeedbackStyle(ImageView up, ImageView down, int feedback) {
        up.setColorFilter(getColor(feedback == 1 ? R.color.blue : R.color.text_sub));
        down.setColorFilter(getColor(feedback == -1 ? R.color.blue : R.color.text_sub));
    }

    /** 이 AI 답변보다 앞의 대화 이력 (재생성 컨텍스트). 직전 사용자 입력은 제외. */
    private ArrayList<PromptData.ChatMessage> historyBefore(PromptData.ChatMessage aiMsg, String promptId) {
        ArrayList<PromptData.ChatMessage> msgs =
                dbHelper.getChatMessages(PromptData.currentUserId, promptId);
        ArrayList<PromptData.ChatMessage> history = new ArrayList<>();
        for (PromptData.ChatMessage m : msgs) {
            if (m.id == aiMsg.id) break;
            history.add(m);
        }
        if (!history.isEmpty()
                && "user".equals(history.get(history.size() - 1).sender)) {
            history.remove(history.size() - 1);
        }
        return history;
    }

    /** "다른 버전으로": 같은 입력을 다시 변환해 새 버전으로 저장하고 < > 로 오갈 수 있게 한다. */
    private void regenerateMessage(PromptData.ChatMessage aiMsg, String originalUserText, String extraNote) {
        final PromptData.Prompt prompt = PromptData.getSelectedPrompt();
        if (prompt == null || originalUserText == null || originalUserText.isEmpty()) {
            Toast.makeText(this, "원본 입력을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "다른 버전을 생성 중입니다...", Toast.LENGTH_SHORT).show();

        String input = originalUserText;
        if (extraNote != null) {
            input = "[재작성 요청] " + extraNote + "\n변환할 메시지: " + originalUserText;
        } else {
            input = "[재작성 요청] 같은 내용을 다른 표현과 문장 구성으로 다시 변환해줘.\n변환할 메시지: " + originalUserText;
        }

        final String promptId = prompt.id;
        toneRepo.correctTone(
                this, prompt.name, prompt.keywords, prompt.persona, prompt.memoryInfo,
                historyBefore(aiMsg, promptId), input,
                new com.example.aitonechanger.data.ToneChatRepository.CorrectionCallback() {
                    @Override
                    public void onSuccess(String correctedText) {
                        dbHelper.addMessageVersion(aiMsg.id, correctedText);
                        if (isFinishing() || isDestroyed()) return;
                        if (promptId.equals(PromptData.selectedPromptId)) {
                            renderChatState();
                            chatScroll.postDelayed(() ->
                                    chatScroll.fullScroll(View.FOCUS_DOWN), 120);
                        } else {
                            Toast.makeText(MainActivity.this,
                                    "[" + prompt.name + "] 다른 버전이 준비되었습니다.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {
                        Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * 답변 직접 수정 (꾹 누르기): AI 재생성 없이 내가 고친 내용을
     * 새 버전으로 그 자리에 저장한다. < > 로 원본과 오갈 수 있다.
     */
    private void showManualEditDialog(PromptData.ChatMessage aiMsg) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(dp(20), dp(10), dp(20), 0);

        final EditText body = new EditText(this);
        body.setText(aiMsg.displayText());
        body.setSelection(body.getText().length());
        body.setTextSize(14);
        body.setMinLines(3);
        body.setMaxLines(10);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setBackgroundResource(R.drawable.rounded_input);
        body.setPadding(dp(12), dp(12), dp(12), dp(12));
        wrap.addView(body, new LinearLayout.LayoutParams(-1, -2));

        new AlertDialog.Builder(this)
                .setTitle("답변 직접 수정")
                .setView(wrap)
                .setNegativeButton("취소", null)
                .setPositiveButton("저장", (d, w) -> {
                    String edited = body.getText().toString().trim();

                    if (edited.isEmpty()) {
                        Toast.makeText(this, "내용을 입력해주세요.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (edited.equals(aiMsg.displayText())) {
                        return; // 변경 없음
                    }

                    dbHelper.addMessageVersion(aiMsg.id, edited);
                    Toast.makeText(this, "수정본이 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    renderChatState();
                })
                .show();
    }

    private TextView actionButton(int iconRes, String label, View.OnClickListener listener) {
        TextView b = new TextView(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(getColor(R.color.text_sub));
        b.setGravity(Gravity.CENTER);
        b.setBackgroundResource(R.drawable.chip_unselected);
        b.setCompoundDrawablesWithIntrinsicBounds(iconRes, 0, 0, 0);
        b.setCompoundDrawablePadding(dp(5));
        b.setOnClickListener(listener);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(dp(90), dp(38));

        lp.rightMargin = dp(8);
        b.setLayoutParams(lp);

        return b;
    }

    private void copyText(String text) {
        ClipboardManager cm =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        cm.setPrimaryClip(ClipData.newPlainText("AI 말투 변환 결과", text));

        Toast.makeText(this, "복사되었습니다", Toast.LENGTH_SHORT).show();
    }

    private void shareText(String text) {
        Intent sendIntent = new Intent(Intent.ACTION_SEND);
        sendIntent.setType("text/plain");
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);

        startActivity(Intent.createChooser(sendIntent, "공유하기"));
    }

    private String getCurrentTimeText() {
        SimpleDateFormat sdf = new SimpleDateFormat("a h:mm", Locale.KOREAN);
        return sdf.format(new Date());
    }

    private void SpaceTop(int h) {
        addSpace(h);
    }

    private void addSpace(int h) {
        View sp = new View(this);
        chatContainer.addView(sp, new LinearLayout.LayoutParams(1, h));
    }

    private void addSpaceTo(LinearLayout target, int h) {
        View sp = new View(this);
        target.addView(sp, new LinearLayout.LayoutParams(1, h));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}