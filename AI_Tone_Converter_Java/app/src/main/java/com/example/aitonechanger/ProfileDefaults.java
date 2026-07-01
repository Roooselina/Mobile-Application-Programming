package com.example.aitonechanger;

import android.content.Context;

/**
 * "나에 대한 정보" 기본값을 한 곳에서 결정한다.
 *
 * 우선순위:
 *   1) 앱 설정(사용자 프로필)에 저장한 이름/소속/직책
 *   2) 설정에 이름이 없으면 회원가입 시 입력한 이름
 *      (내 정보 수정에서 이름을 바꿨다면 바뀐 이름)
 *
 * 프롬프트별 "나에 대한 정보"가 비어 있을 때 이 값이 자동으로 쓰인다.
 */
public final class ProfileDefaults {

    private ProfileDefaults() {
    }

    /** 서명/자기소개에 쓸 기본 이름. 설정 프로필 이름 → 계정 이름 순. 없으면 "". */
    public static String resolveName(Context context) {
        String name = AppPrefs.getProfileName(context);

        if (notBlank(name)) {
            return name.trim();
        }

        if (PromptData.currentUserId != -1) {
            DatabaseHelper dbHelper = new DatabaseHelper(context);
            String[] userInfo = dbHelper.getUserProfile(PromptData.currentUserId);
            String accountName = userInfo[0];

            if (notBlank(accountName)) {
                return accountName.trim();
            }
        }

        return "";
    }

    /**
     * 프롬프트의 "나에 대한 정보"가 비어 있을 때 대신 쓸 텍스트를 만든다.
     * 설정 프로필(이름/소속/직책)을 한 줄 정보로 합치고,
     * 그것도 없으면 계정 이름만 반환한다. 아무것도 없으면 "".
     */
    public static String buildFallbackAboutMe(Context context) {
        String name = resolveName(context);
        String affiliation = AppPrefs.getAffiliation(context);
        String position = AppPrefs.getPosition(context);

        StringBuilder sb = new StringBuilder();

        if (notBlank(name)) {
            sb.append("이름: ").append(name.trim());
        }
        if (notBlank(affiliation)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("소속: ").append(affiliation.trim());
        }
        if (notBlank(position)) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("직책: ").append(position.trim());
        }

        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
