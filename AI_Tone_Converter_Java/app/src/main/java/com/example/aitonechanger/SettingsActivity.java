package com.example.aitonechanger;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

/**
 * App settings screen.
 *
 * Functional sections:
 *   - AI 설정 (변환 강도 / 응답 길이): persisted and fed into the tone prompt.
 *   - 사용자 프로필 (이름/소속/직책 + 특정 표현 금지): persisted and added to the prompt.
 *   - 화면 설정 (다크/일반 모드, 글자 크기): applied app-wide via BaseActivity.
 *
 * UI-only section (no real logic, per spec):
 *   - 사용량 및 결제 rows just show a "준비 중" toast.
 */
public class SettingsActivity extends BaseActivity {

    private int strengthSel;
    private int lengthSel;
    private int themeSel;
    private int fontSel;

    private TextView segStrengthKeep, segStrengthBalanced;
    private TextView segLenShort, segLenNormal, segLenDetailed;
    private TextView segThemeSystem, segThemeLight, segThemeDark;
    private TextView segFontSmall, segFontNormal, segFontLarge, segFontXLarge;

    private EditText editProfileName, editAffiliation, editPosition, editForbidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        applyInsetsToRoot();
        shrinkHints();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        bindViews();
        loadCurrentValues();
        setupSegmentHandlers();
        setupBillingRows();

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> save());
    }

    private void bindViews() {
        segStrengthKeep = findViewById(R.id.segStrengthKeep);
        segStrengthBalanced = findViewById(R.id.segStrengthBalanced);

        segLenShort = findViewById(R.id.segLenShort);
        segLenNormal = findViewById(R.id.segLenNormal);
        segLenDetailed = findViewById(R.id.segLenDetailed);

        segThemeSystem = findViewById(R.id.segThemeSystem);
        segThemeLight = findViewById(R.id.segThemeLight);
        segThemeDark = findViewById(R.id.segThemeDark);

        segFontSmall = findViewById(R.id.segFontSmall);
        segFontNormal = findViewById(R.id.segFontNormal);
        segFontLarge = findViewById(R.id.segFontLarge);
        segFontXLarge = findViewById(R.id.segFontXLarge);

        editProfileName = findViewById(R.id.editProfileName);
        editAffiliation = findViewById(R.id.editAffiliation);
        editPosition = findViewById(R.id.editPosition);
        editForbidden = findViewById(R.id.editForbidden);
    }

    private void loadCurrentValues() {
        strengthSel = AppPrefs.getStrength(this);
        lengthSel = AppPrefs.getLength(this);
        themeSel = AppPrefs.getThemeMode(this);
        fontSel = AppPrefs.getFontStep(this);

        // 설정에 저장된 이름이 없으면 계정 이름(회원가입/내 정보 수정의 이름)을 기본값으로 보여준다.
        String profileName = AppPrefs.getProfileName(this);
        if (profileName == null || profileName.trim().isEmpty()) {
            profileName = ProfileDefaults.resolveName(this);
        }
        editProfileName.setText(profileName);

        android.widget.Switch live = findViewById(R.id.switchLivePreview);
        live.setChecked(AppPrefs.isLivePreview(this));
        live.setOnCheckedChangeListener((b, on) -> AppPrefs.setLivePreview(this, on));
        editAffiliation.setText(AppPrefs.getAffiliation(this));
        editPosition.setText(AppPrefs.getPosition(this));
        editForbidden.setText(AppPrefs.getForbidden(this));

        renderAll();
    }

    private void setupSegmentHandlers() {
        segStrengthKeep.setOnClickListener(v -> {
            strengthSel = AppPrefs.STRENGTH_KEEP;
            renderAll();
        });
        segStrengthBalanced.setOnClickListener(v -> {
            strengthSel = AppPrefs.STRENGTH_BALANCED;
            renderAll();
        });

        segLenShort.setOnClickListener(v -> {
            lengthSel = AppPrefs.LENGTH_SHORT;
            renderAll();
        });
        segLenNormal.setOnClickListener(v -> {
            lengthSel = AppPrefs.LENGTH_NORMAL;
            renderAll();
        });
        segLenDetailed.setOnClickListener(v -> {
            lengthSel = AppPrefs.LENGTH_DETAILED;
            renderAll();
        });

        segThemeSystem.setOnClickListener(v -> {
            themeSel = AppPrefs.THEME_SYSTEM;
            renderAll();
        });
        segThemeLight.setOnClickListener(v -> {
            themeSel = AppPrefs.THEME_LIGHT;
            renderAll();
        });
        segThemeDark.setOnClickListener(v -> {
            themeSel = AppPrefs.THEME_DARK;
            renderAll();
        });

        segFontSmall.setOnClickListener(v -> {
            fontSel = 0;
            renderAll();
        });
        segFontNormal.setOnClickListener(v -> {
            fontSel = 1;
            renderAll();
        });
        segFontLarge.setOnClickListener(v -> {
            fontSel = 2;
            renderAll();
        });
        segFontXLarge.setOnClickListener(v -> {
            fontSel = 3;
            renderAll();
        });
    }

    private void setupBillingRows() {
        View.OnClickListener mock = v ->
                Toast.makeText(this, "준비 중인 기능입니다", Toast.LENGTH_SHORT).show();
        findViewById(R.id.rowCredit).setOnClickListener(mock);
        findViewById(R.id.rowHistory).setOnClickListener(mock);
        findViewById(R.id.rowRecharge).setOnClickListener(mock);
        findViewById(R.id.rowSubscription).setOnClickListener(mock);
        findViewById(R.id.rowDailyUsage).setOnClickListener(mock);
    }

    private void renderAll() {
        renderGroup(strengthSel, segStrengthKeep, segStrengthBalanced);
        renderGroup(lengthSel, segLenShort, segLenNormal, segLenDetailed);
        renderGroup(themeSel, segThemeSystem, segThemeLight, segThemeDark);
        renderGroup(fontSel, segFontSmall, segFontNormal, segFontLarge, segFontXLarge);
    }

    /** Highlights the option at index `selected` within the given group. */
    private void renderGroup(int selected, TextView... items) {
        for (int i = 0; i < items.length; i++) {
            boolean on = (i == selected);
            items[i].setBackgroundResource(on ? R.drawable.seg_selected : R.drawable.seg_unselected);
            items[i].setTextColor(on ? getColor(R.color.blue) : getColor(R.color.text_main));
            items[i].setTypeface(null, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    private void save() {
        AppPrefs.setStrength(this, strengthSel);
        AppPrefs.setLength(this, lengthSel);
        AppPrefs.setProfileContext(
                this,
                editProfileName.getText().toString().trim(),
                editAffiliation.getText().toString().trim(),
                editPosition.getText().toString().trim(),
                editForbidden.getText().toString().trim()
        );

        boolean themeChanged = themeSel != AppPrefs.getThemeMode(this);
        boolean fontChanged = fontSel != AppPrefs.getFontStep(this);

        AppPrefs.setThemeMode(this, themeSel);
        AppPrefs.setFontStep(this, fontSel);

        Toast.makeText(this, "저장되었습니다", Toast.LENGTH_SHORT).show();

        if (themeChanged) {
            // 다크/일반 전환: 대상 배경색으로 부드럽게 페이드 후 테마 적용
            fadeOutThenRecreate(resolveBgForMode(themeSel));
        } else if (fontChanged) {
            recreate();
        }
    }

    /** Resolves @color/bg as it will appear in the target theme mode. */
    private int resolveBgForMode(int mode) {
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
}
