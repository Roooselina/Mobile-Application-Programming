package com.example.aitonechanger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;

/**
 * Common base for every screen.
 *
 * Two responsibilities:
 *   1. Apply the saved display preferences (dark/light mode, font scale) by
 *      overriding the Configuration in attachBaseContext. This works without
 *      AppCompat: the overridden uiMode selects values-night resources, and
 *      fontScale rescales all sp-based text.
 *   2. Provide applyTopInset() so each screen drops its content below the
 *      status bar / camera punch-hole using the real per-device inset height
 *      instead of a fixed dp guess.
 */
public class BaseActivity extends Activity {

    private int appliedThemeMode;
    private int appliedFontStep;

    // 다크/일반 전환 시 부드러운 색 전환을 위한 상태 (recreate를 가로질러 전달)
    public static boolean sThemeTransition = false;
    public static int sThemeColor = 0xFF121212;

    @Override
    protected void attachBaseContext(Context base) {
        Configuration config = new Configuration(base.getResources().getConfiguration());

        int themeMode = AppPrefs.getThemeMode(base);
        int currentNight = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        int night;
        if (themeMode == AppPrefs.THEME_LIGHT) {
            night = Configuration.UI_MODE_NIGHT_NO;
        } else if (themeMode == AppPrefs.THEME_DARK) {
            night = Configuration.UI_MODE_NIGHT_YES;
        } else {
            night = currentNight; // follow system
        }
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;

        config.fontScale = AppPrefs.getFontScale(base);

        appliedThemeMode = themeMode;
        appliedFontStep = AppPrefs.getFontStep(base);

        Context ctx = base.createConfigurationContext(config);
        super.attachBaseContext(ctx);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (sThemeTransition) {
            sThemeTransition = false;
            final int color = sThemeColor;
            getWindow().getDecorView().post(() -> playThemeReveal(color));
        }
    }

    /**
     * Fades the screen to targetColor, then applies the saved theme via
     * recreate(). The recreated screen reveals the new theme by fading the
     * same color back out -> a smooth white<->black style transition.
     */
    protected void fadeOutThenRecreate(final int targetColor) {
        ViewGroup content = (ViewGroup) findViewById(android.R.id.content);
        if (content == null) {
            recreate();
            return;
        }
        final View overlay = new View(this);
        overlay.setBackgroundColor(targetColor);
        overlay.setAlpha(0f);
        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.animate().alpha(1f).setDuration(280)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> {
                    sThemeTransition = true;
                    sThemeColor = targetColor;
                    // recreate 사이의 빈 프레임이 흰색으로 번쩍이지 않도록
                    // 윈도우 배경과 시스템 바 색을 목표 색으로 미리 맞춰둔다.
                    getWindow().setBackgroundDrawable(
                            new android.graphics.drawable.ColorDrawable(targetColor));
                    getWindow().setStatusBarColor(targetColor);
                    getWindow().setNavigationBarColor(targetColor);
                    recreate();
                }).start();
    }

    private void playThemeReveal(int color) {
        ViewGroup content = (ViewGroup) findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        final View overlay = new View(this);
        overlay.setBackgroundColor(color);
        overlay.setAlpha(1f);
        content.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.animate().alpha(0f).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .withEndAction(() -> content.removeView(overlay)).start();
    }

    /** 현재(또는 지정한) 테마 모드 기준의 @color/bg 값을 구한다. */
    protected int resolveThemeBg(int mode) {
        Configuration c = new Configuration(getResources().getConfiguration());
        int mask = Configuration.UI_MODE_NIGHT_MASK;
        int night;
        if (mode == AppPrefs.THEME_LIGHT) {
            night = Configuration.UI_MODE_NIGHT_NO;
        } else if (mode == AppPrefs.THEME_DARK) {
            night = Configuration.UI_MODE_NIGHT_YES;
        } else {
            night = c.uiMode & mask;
        }
        c.uiMode = (c.uiMode & ~mask) | night;
        Context ctx = createConfigurationContext(c);
        return ctx.getColor(R.color.bg);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // If the user changed theme/font on another screen, re-apply here.
        boolean themeChanged = AppPrefs.getThemeMode(this) != appliedThemeMode;
        boolean fontChanged = AppPrefs.getFontStep(this) != appliedFontStep;

        if (themeChanged) {
            // 다른 화면에서 테마가 바뀐 채 돌아온 경우에도
            // 번쩍임 없이 목표 배경색으로 부드럽게 전환한다.
            fadeOutThenRecreate(resolveThemeBg(AppPrefs.getThemeMode(this)));
        } else if (fontChanged) {
            recreate();
        }
    }

    // ----- 화면 전환 애니메이션 (부드러운 슬라이드 + 페이드) -----

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        overridePendingTransition(R.anim.anim_enter, R.anim.anim_exit);
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.anim_pop_enter, R.anim.anim_pop_exit);
    }

    /**
     * 화면 안 모든 EditText의 hint 글자 크기만 줄인다(입력 글자 크기는 유지).
     * hint가 본문과 같은 크기로 보이면 화면이 답답해 보여서, 본문(14sp)보다 살짝 작은 13sp로 맞춘다.
     */
    protected void shrinkHints() {
        View content = findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            shrinkHintsIn((ViewGroup) content, 13);
        }
    }

    private void shrinkHintsIn(ViewGroup group, int sp) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof android.widget.EditText) {
                android.widget.EditText et = (android.widget.EditText) child;
                CharSequence hint = et.getHint();
                if (hint != null && hint.length() > 0
                        && !(hint instanceof android.text.Spanned)) {
                    android.text.SpannableString s = new android.text.SpannableString(hint);
                    s.setSpan(new android.text.style.AbsoluteSizeSpan(sp, true),
                            0, s.length(),
                            android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    et.setHint(s);
                }
            } else if (child instanceof ViewGroup) {
                shrinkHintsIn((ViewGroup) child, sp);
            }
        }
    }

    /**
     * Pads the content root by the real per-device system-bar insets:
     * top = status bar / punch-hole height, bottom = navigation bar height.
     * Reads the actual inset instead of a fixed dp, so it is correct on every
     * device regardless of notch or punch-hole size.
     */
    protected void applyInsetsToRoot() {
        View content = findViewById(android.R.id.content);
        if (!(content instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) content;
        if (group.getChildCount() == 0) {
            return;
        }
        applySystemBarInsets(group.getChildAt(0));
    }

    /**
     * Applies status-bar top and navigation-bar bottom insets to a single view,
     * preserving its existing padding.
     */
    protected void applySystemBarInsets(final View target) {
        if (target == null) {
            return;
        }
        final int baseLeft = target.getPaddingLeft();
        final int baseTop = target.getPaddingTop();
        final int baseRight = target.getPaddingRight();
        final int baseBottom = target.getPaddingBottom();

        target.setOnApplyWindowInsetsListener((v, insets) -> {
            int topInset;
            int bottomInset;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());
                topInset = bars.top;
                bottomInset = bars.bottom;
            } else {
                topInset = insets.getSystemWindowInsetTop();
                bottomInset = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(baseLeft, baseTop + topInset, baseRight, baseBottom + bottomInset);
            return insets;
        });
        target.requestApplyInsets();
    }
}
