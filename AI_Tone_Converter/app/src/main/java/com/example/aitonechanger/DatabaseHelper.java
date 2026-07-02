package com.example.aitonechanger;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.Arrays;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "ai_tone_converter.db"; // 모바일 db 연결해서 확인
    private static final int DB_VERSION = 10;

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

        db.execSQL("CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT," +
                "email TEXT UNIQUE," +
                "nickname TEXT," +
                "password TEXT)");

        db.execSQL("CREATE TABLE IF NOT EXISTS prompts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "title TEXT," +
                "keywords TEXT," +
                "persona TEXT," +
                "memory_info TEXT," +
                "grp TEXT DEFAULT ''," +
                "favorite INTEGER DEFAULT 0," +
                "FOREIGN KEY(user_id) REFERENCES users(id))");

        db.execSQL("CREATE TABLE IF NOT EXISTS keywords (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "keyword TEXT," +
                "FOREIGN KEY(user_id) REFERENCES users(id))");

        db.execSQL("CREATE TABLE IF NOT EXISTS chat_messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "user_id INTEGER," +
                "prompt_id TEXT," +
                "sender TEXT," +
                "message TEXT," +
                "time_text TEXT," +
                "current_version INTEGER DEFAULT 0," +
                "feedback INTEGER DEFAULT 0," +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY(user_id) REFERENCES users(id))");

        // AI 답변의 추가 버전들 ("다른 버전으로" 생성분). 원본은 chat_messages.message.
        db.execSQL("CREATE TABLE IF NOT EXISTS message_versions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "message_id INTEGER," +
                "content TEXT," +
                "FOREIGN KEY(message_id) REFERENCES chat_messages(id))");
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 다른 빌드(상위 버전 DB)가 설치돼 있던 경우에도 크래시 없이 초기화
        onUpgrade(db, oldVersion, newVersion);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS message_versions");
        db.execSQL("DROP TABLE IF EXISTS chat_messages");
        db.execSQL("DROP TABLE IF EXISTS keywords");
        db.execSQL("DROP TABLE IF EXISTS prompts");
        db.execSQL("DROP TABLE IF EXISTS users");
        onCreate(db);
    }

    /** 프롬프트를 저장하고 생성된 row id를 반환한다. 실패 시 -1. */
    public long insertPromptReturnId(int userId, String title, ArrayList<String> keywords,
                                     String persona, String memoryInfo, String group) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("title", title);
        values.put("keywords", android.text.TextUtils.join(",", keywords));
        values.put("persona", persona == null ? "" : persona);
        values.put("memory_info", memoryInfo == null ? "" : memoryInfo);
        values.put("grp", group == null ? "" : group.trim());
        values.put("favorite", 0);

        return db.insert("prompts", null, values);
    }

    public ArrayList<PromptData.Prompt> getPromptsByUserId(int userId) {
        ArrayList<PromptData.Prompt> list = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id, title, keywords, favorite, persona, memory_info, grp FROM prompts WHERE user_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(userId)}
        );

        while (cursor.moveToNext()) {
            String id = String.valueOf(cursor.getInt(0));
            String title = cursor.getString(1);
            String keywordsText = cursor.getString(2);
            boolean favorite = cursor.getInt(3) == 1;
            String persona = cursor.getString(4);
            String memoryInfo = cursor.getString(5);

            ArrayList<String> keywords = new ArrayList<>();

            if (keywordsText != null && !keywordsText.isEmpty()) {
                keywords.addAll(Arrays.asList(keywordsText.split(",")));
            }

            PromptData.Prompt p = new PromptData.Prompt(id, title, keywords, favorite, persona, memoryInfo);
            String grp = cursor.getString(6);
            p.group = grp == null ? "" : grp;
            list.add(p);
        }

        cursor.close();
        return list;
    }

    public boolean updatePrompt(String id, String title, ArrayList<String> keywords,
                                String persona, String memoryInfo) {
        return updatePrompt(id, title, keywords, persona, memoryInfo, null);
    }

    /** group이 null이면 기존 그룹 유지 */
    public boolean updatePrompt(String id, String title, ArrayList<String> keywords,
                                String persona, String memoryInfo, String group) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        if (group != null) values.put("grp", group.trim());
        values.put("title", title);
        values.put("keywords", android.text.TextUtils.join(",", keywords));
        values.put("persona", persona == null ? "" : persona);
        values.put("memory_info", memoryInfo == null ? "" : memoryInfo);

        int result = db.update(
                "prompts",
                values,
                "id = ?",
                new String[]{id}
        );

        return result > 0;
    }

    public boolean deletePrompt(String id) {
        SQLiteDatabase db = this.getWritableDatabase();

        db.execSQL("DELETE FROM message_versions WHERE message_id IN " +
                "(SELECT id FROM chat_messages WHERE prompt_id = ?)", new String[]{id});

        db.delete(
                "chat_messages",
                "prompt_id = ?",
                new String[]{id}
        );

        int result = db.delete(
                "prompts",
                "id = ?",
                new String[]{id}
        );

        return result > 0;
    }

    public boolean updateFavorite(String id, boolean favorite) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("favorite", favorite ? 1 : 0);

        int result = db.update(
                "prompts",
                values,
                "id = ?",
                new String[]{id}
        );

        return result > 0;
    }

    public void insertDefaultKeywordsIfNeeded(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM keywords WHERE user_id = ?",
                new String[]{String.valueOf(userId)}
        );

        int count = 0;

        if (cursor.moveToFirst()) {
            count = cursor.getInt(0);
        }

        cursor.close();

        if (count > 0) return;

        String[] defaults = {
                "정중함", "부드러움", "따뜻함", "간결함", "딱딱함",
                "전문적", "단호함", "사과표현", "공손함", "자연스러움",
                "차분함", "밝음", "친근함", "센스있음", "설득력",
                "부담없음", "감성적", "예의바름", "짧게", "유쾌함"
        };

        for (String keyword : defaults) {
            ContentValues values = new ContentValues();
            values.put("user_id", userId);
            values.put("keyword", keyword);
            db.insert("keywords", null, values);
        }
    }

    public ArrayList<String> getKeywordsByUserId(int userId) {
        ArrayList<String> list = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT keyword FROM keywords WHERE user_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(userId)}
        );

        while (cursor.moveToNext()) {
            list.add(cursor.getString(0));
        }

        cursor.close();
        return list;
    }

    public boolean insertKeyword(int userId, String keyword) {
        SQLiteDatabase db = this.getWritableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id FROM keywords WHERE user_id = ? AND keyword = ?",
                new String[]{String.valueOf(userId), keyword}
        );

        if (cursor.moveToFirst()) {
            cursor.close();
            return false;
        }

        cursor.close();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("keyword", keyword);

        long result = db.insert("keywords", null, values);
        return result != -1;
    }

    public boolean deleteKeyword(int userId, String keyword) {
        SQLiteDatabase db = this.getWritableDatabase();

        int result = db.delete(
                "keywords",
                "user_id = ? AND keyword = ?",
                new String[]{String.valueOf(userId), keyword}
        );

        return result > 0;
    }

    public boolean updateKeyword(int userId, String oldKeyword, String newKeyword) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("keyword", newKeyword);

        int result = db.update(
                "keywords",
                values,
                "user_id = ? AND keyword = ?",
                new String[]{String.valueOf(userId), oldKeyword}
        );

        return result > 0;
    }

    public boolean insertChatMessage(int userId, String promptId, String sender, String message, String timeText) {
        return insertChatMessageReturnId(userId, promptId, sender, message, timeText) != -1;
    }

    /** 메시지를 저장하고 row id를 반환한다 (버전/피드백 관리에 사용). 실패 시 -1. */
    public long insertChatMessageReturnId(int userId, String promptId, String sender, String message, String timeText) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("user_id", userId);
        values.put("prompt_id", promptId);
        values.put("sender", sender);
        values.put("message", message);
        values.put("time_text", timeText);

        return db.insert("chat_messages", null, values);
    }

    public ArrayList<PromptData.ChatMessage> getChatMessages(int userId, String promptId) {
        ArrayList<PromptData.ChatMessage> list = new ArrayList<>();

        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT id, sender, message, time_text, current_version, feedback " +
                        "FROM chat_messages WHERE user_id = ? AND prompt_id = ? ORDER BY id ASC",
                new String[]{String.valueOf(userId), promptId}
        );

        while (cursor.moveToNext()) {
            PromptData.ChatMessage m = new PromptData.ChatMessage(
                    cursor.getString(1), cursor.getString(2), cursor.getString(3));
            m.id = cursor.getLong(0);
            m.currentVersion = cursor.getInt(4);
            m.feedback = cursor.getInt(5);
            list.add(m);
        }

        cursor.close();

        // AI 메시지의 추가 버전 로드
        for (PromptData.ChatMessage m : list) {
            if (!"ai".equals(m.sender)) continue;
            Cursor vc = db.rawQuery(
                    "SELECT content FROM message_versions WHERE message_id = ? ORDER BY id ASC",
                    new String[]{String.valueOf(m.id)});
            while (vc.moveToNext()) {
                m.altVersions.add(vc.getString(0));
            }
            vc.close();
        }

        return list;
    }

    public String getLastChatMessage(int userId, String promptId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT message FROM chat_messages " +
                        "WHERE user_id = ? AND prompt_id = ? " +
                        "ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(userId), promptId}
        );

        String message = "대화 내용이 없습니다.";

        if (cursor.moveToFirst()) {
            message = cursor.getString(0);
        }

        cursor.close();
        return message;
    }

    public boolean updateUserProfile(int userId, String name, String email, String nickname) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("nickname", nickname);

        int result = db.update(
                "users",
                values,
                "id = ?",
                new String[]{String.valueOf(userId)}
        );

        return result > 0;
    }

    public String[] getUserProfile(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT name, email, nickname FROM users WHERE id = ?",
                new String[]{String.valueOf(userId)}
        );

        String[] userInfo = new String[]{"", "", ""};

        if (cursor.moveToFirst()) {
            userInfo[0] = cursor.getString(0);
            userInfo[1] = cursor.getString(1);
            userInfo[2] = cursor.getString(2);
        }

        cursor.close();
        return userInfo;
    }

    /** 현재 비밀번호 검증 (저장값은 PasswordUtil 해시) */
    public boolean checkCurrentPassword(int userId, String currentPassword) {
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT password FROM users WHERE id = ?",
                new String[]{String.valueOf(userId)}
        );

        String stored = cursor.moveToFirst() ? cursor.getString(0) : null;
        cursor.close();

        return PasswordUtil.verify(currentPassword, stored);
    }

    /** 비밀번호 변경 (해시로 저장) */
    public boolean updatePassword(int userId, String newPassword) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("password", PasswordUtil.hash(newPassword));

        int result = db.update(
                "users",
                values,
                "id = ?",
                new String[]{String.valueOf(userId)}
        );

        return result > 0;
    }

    // ----- 메시지 버전 / 피드백 -----

    /** "다른 버전으로" 생성된 답변 저장 후 해당 버전을 현재 버전으로 선택. 새 버전 번호 반환. */
    public int addMessageVersion(long messageId, String content) {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("message_id", messageId);
        values.put("content", content);
        db.insert("message_versions", null, values);

        Cursor cursor = db.rawQuery(
                "SELECT COUNT(*) FROM message_versions WHERE message_id = ?",
                new String[]{String.valueOf(messageId)});
        int altCount = cursor.moveToFirst() ? cursor.getInt(0) : 0;
        cursor.close();

        setCurrentVersion(messageId, altCount); // 0=원본, altCount=마지막 추가 버전
        return altCount;
    }

    /** <  > 버튼으로 보고 있는 버전 변경 */
    public void setCurrentVersion(long messageId, int versionIndex) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("current_version", versionIndex);
        db.update("chat_messages", values, "id = ?", new String[]{String.valueOf(messageId)});
    }

    /** 답변 피드백 저장: 1=👍, -1=👎, 0=해제 */
    public void setMessageFeedback(long messageId, int feedback) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("feedback", feedback);
        db.update("chat_messages", values, "id = ?", new String[]{String.valueOf(messageId)});
    }

    /** 최근 입력 히스토리: 이 대화에서 보낸 사용자 입력 중 최근 것부터, 중복 제거 */
    public ArrayList<String> getRecentUserInputs(int userId, String promptId, int limit) {
        ArrayList<String> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT message FROM chat_messages " +
                        "WHERE user_id = ? AND prompt_id = ? AND sender = 'user' " +
                        "GROUP BY message ORDER BY MAX(id) DESC LIMIT ?",
                new String[]{String.valueOf(userId), promptId, String.valueOf(limit)});

        while (cursor.moveToNext()) {
            list.add(cursor.getString(0));
        }
        cursor.close();
        return list;
    }

    /** 비밀번호 재설정 (이메일 인증 후 사용). 저장값은 PasswordUtil 해시. */
    public boolean updatePasswordByEmail(String email, String hashedPassword) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("password", hashedPassword);
        int result = db.update("users", values, "email = ?", new String[]{email});
        return result > 0;
    }

    /** 세션 복원 시 해당 사용자가 아직 존재하는지 확인 */
    public boolean userExists(int userId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT id FROM users WHERE id = ?",
                new String[]{String.valueOf(userId)}
        );
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    /**
     * 회원 탈퇴: 해당 사용자와 관련된 모든 데이터를 삭제한다.
     * (메시지 버전 -> 채팅 -> 키워드 -> 프롬프트 -> 사용자 순서, 트랜잭션 처리)
     */
    public boolean deleteUser(int userId) {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = new String[]{String.valueOf(userId)};
        db.beginTransaction();
        try {
            db.execSQL(
                    "DELETE FROM message_versions WHERE message_id IN " +
                            "(SELECT id FROM chat_messages WHERE user_id = ?)",
                    new Object[]{userId});
            db.delete("chat_messages", "user_id = ?", args);
            db.delete("keywords", "user_id = ?", args);
            db.delete("prompts", "user_id = ?", args);
            int result = db.delete("users", "id = ?", args);
            db.setTransactionSuccessful();
            return result > 0;
        } finally {
            db.endTransaction();
        }
    }
}
