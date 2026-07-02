package com.example.aitonechanger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Single place for all persisted app settings.
 *
 * Display:
 *   - themeMode: 0 system / 1 light / 2 dark  (applied in BaseActivity)
 *   - fontScaleStep: 0 small / 1 normal / 2 large / 3 x-large
 *
 * AI settings (fed into the tone-correction system prompt):
 *   - strength: 0 = keep original as much as possible, 1 = balanced
 *   - length:   0 = short, 1 = normal, 2 = detailed
 *
 * User profile context (optional, appended to the prompt):
 *   - profileName / affiliation / position / forbiddenExpressions
 *
 * Billing values here are display-only placeholders; no real billing logic.
 */
public final class AppPrefs {

    private static final String PREF = "app_settings";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int STRENGTH_KEEP = 0;
    public static final int STRENGTH_BALANCED = 1;

    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_NORMAL = 1;
    public static final int LENGTH_DETAILED = 2;

    private static final String K_THEME = "theme_mode";
    private static final String K_FONT = "font_step";
    private static final String K_STRENGTH = "ai_strength";
    private static final String K_LENGTH = "ai_length";
    private static final String K_PROFILE_NAME = "profile_name";
    private static final String K_AFFILIATION = "profile_affiliation";
    private static final String K_POSITION = "profile_position";
    private static final String K_FORBIDDEN = "forbidden_expressions";

    private AppPrefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    // ----- Display -----

    public static int getThemeMode(Context c) {
        return sp(c).getInt(K_THEME, THEME_SYSTEM);
    }

    public static void setThemeMode(Context c, int mode) {
        sp(c).edit().putInt(K_THEME, mode).apply();
    }

    public static int getFontStep(Context c) {
        return sp(c).getInt(K_FONT, 1);
    }

    public static void setFontStep(Context c, int step) {
        sp(c).edit().putInt(K_FONT, step).apply();
    }

    public static float getFontScale(Context c) {
        switch (getFontStep(c)) {
            case 0:
                return 0.9f;
            case 2:
                return 1.15f;
            case 3:
                return 1.3f;
            case 1:
            default:
                return 1.0f;
        }
    }

    // ----- AI settings -----

    public static int getStrength(Context c) {
        return sp(c).getInt(K_STRENGTH, STRENGTH_BALANCED);
    }

    public static void setStrength(Context c, int v) {
        sp(c).edit().putInt(K_STRENGTH, v).apply();
    }

    public static int getLength(Context c) {
        return sp(c).getInt(K_LENGTH, LENGTH_NORMAL);
    }

    public static void setLength(Context c, int v) {
        sp(c).edit().putInt(K_LENGTH, v).apply();
    }

    // ----- User profile context -----

    public static String getProfileName(Context c) {
        return sp(c).getString(K_PROFILE_NAME, "");
    }

    public static String getAffiliation(Context c) {
        return sp(c).getString(K_AFFILIATION, "");
    }

    public static String getPosition(Context c) {
        return sp(c).getString(K_POSITION, "");
    }

    public static String getForbidden(Context c) {
        return sp(c).getString(K_FORBIDDEN, "");
    }

    public static void setProfileContext(Context c, String name, String affiliation,
                                         String position, String forbidden) {
        sp(c).edit()
                .putString(K_PROFILE_NAME, name)
                .putString(K_AFFILIATION, affiliation)
                .putString(K_POSITION, position)
                .putString(K_FORBIDDEN, forbidden)
                .apply();
    }

    // ----- 실시간 변환 미리보기 -----

    private static final String K_LIVE_PREVIEW = "live_preview";

    /** 입력을 멈추면 자동으로 변환 미리보기를 보여줄지 여부 (기본 꺼짐: API 사용량 절약) */
    public static boolean isLivePreview(Context c) {
        return sp(c).getBoolean(K_LIVE_PREVIEW, false);
    }

    public static void setLivePreview(Context c, boolean on) {
        sp(c).edit().putBoolean(K_LIVE_PREVIEW, on).apply();
    }

    // ----- 입력 드래프트 (프롬프트별 자동 저장) -----

    /** 앱을 꺼도 입력하던 내용이 복원되도록 프롬프트별로 저장한다. */
    public static void setDraft(Context c, String promptId, String text) {
        if (promptId == null) return;
        if (text == null || text.isEmpty()) {
            sp(c).edit().remove("draft_" + promptId).apply();
        } else {
            sp(c).edit().putString("draft_" + promptId, text).apply();
        }
    }

    public static String getDraft(Context c, String promptId) {
        if (promptId == null) return "";
        return sp(c).getString("draft_" + promptId, "");
    }
}
