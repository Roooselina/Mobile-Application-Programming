package com.example.aitonechanger;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Keeps the user logged in across app launches.
 *
 * Stores only the logged-in user's id in SharedPreferences. On launch the
 * splash screen reads it and, if valid, goes straight to the main screen,
 * skipping login. Cleared on logout or account deletion.
 */
public final class Session {

    private static final String PREF = "session";
    private static final String K_UID = "user_id";

    private Session() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void setLoggedIn(Context c, int userId) {
        sp(c).edit().putInt(K_UID, userId).apply();
    }

    public static int getUserId(Context c) {
        return sp(c).getInt(K_UID, -1);
    }

    public static boolean isLoggedIn(Context c) {
        return getUserId(c) > 0;
    }

    public static void clear(Context c) {
        sp(c).edit().remove(K_UID).apply();
    }
}
