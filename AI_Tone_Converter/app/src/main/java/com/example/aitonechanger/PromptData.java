package com.example.aitonechanger;

import java.util.ArrayList;

public class PromptData {

    public static int currentUserId = -1;

    public static class Prompt {
        public String id;
        public String name;
        public ArrayList<String> keywords;
        public boolean favorite;
        public String persona;      // 이 프롬프트(대화)에 적용할 '나에 대한 정보'
        public String memoryInfo;   // 반드시 기억/반영해야 할 정보
        public String group = "";   // 폴더/그룹 (직장, 학교 등). 빈 값이면 미분류

        public Prompt(String id, String name, ArrayList<String> keywords, boolean favorite) {
            this(id, name, keywords, favorite, "", "");
        }

        public Prompt(String id, String name, ArrayList<String> keywords, boolean favorite,
                      String persona, String memoryInfo) {
            this.id = id;
            this.name = name;
            this.keywords = keywords;
            this.favorite = favorite;
            this.persona = persona == null ? "" : persona;
            this.memoryInfo = memoryInfo == null ? "" : memoryInfo;
        }
    }

    public static class ChatMessage {
        public long id;             // chat_messages row id (버전/피드백 관리용)
        public String sender;
        public String message;      // 원본(버전 0) 내용
        public String timeText;

        // AI 메시지 다중 버전: altVersions.get(i) = 버전 i+1, currentVersion 0 = 원본
        public ArrayList<String> altVersions = new ArrayList<>();
        public int currentVersion = 0;
        public int feedback = 0;    // 1 = 👍, -1 = 👎, 0 = 없음

        public ChatMessage(String sender, String message, String timeText) {
            this.sender = sender;
            this.message = message;
            this.timeText = timeText;
        }

        /** 현재 선택된 버전의 내용 (대화 표시·API 히스토리 모두 이 값을 쓴다) */
        public String displayText() {
            if (currentVersion <= 0 || altVersions.isEmpty()) return message;
            int idx = currentVersion - 1;
            if (idx >= altVersions.size()) return message;
            return altVersions.get(idx);
        }

        /** 전체 버전 수 (원본 포함) */
        public int versionCount() {
            return 1 + altVersions.size();
        }
    }

    public static final int MAX_KEYWORD_COUNT = 25;
    public static final int MAX_SELECTED_KEYWORD_COUNT = 5;

    public static ArrayList<Prompt> prompts = new ArrayList<>();

    public static String selectedPromptId = null;
    public static String editingPromptId = null;

    public static Prompt getPrompt(String id) {
        if (id == null) return null;

        for (Prompt p : prompts) {
            if (p.id.equals(id)) return p;
        }

        return null;
    }

    public static Prompt getSelectedPrompt() {
        return getPrompt(selectedPromptId);
    }

    public static boolean canAddKeyword(ArrayList<String> keywords) {
        return keywords.size() < MAX_KEYWORD_COUNT;
    }
}