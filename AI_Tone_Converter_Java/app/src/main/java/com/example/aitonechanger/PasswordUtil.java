package com.example.aitonechanger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 비밀번호 해시 유틸 (SHA-256 + 랜덤 솔트).
 *
 * DB에는 비밀번호 원문 대신 "솔트$해시" 형태의 문자열만 저장한다.
 * - 같은 비밀번호라도 사용자마다 솔트가 달라 해시가 다르다.
 * - DB가 유출돼도 원문 비밀번호를 알 수 없다.
 */
public final class PasswordUtil {

    private PasswordUtil() {
    }

    /** 비밀번호 → "솔트$해시" 저장용 문자열 */
    public static String hash(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        String saltHex = toHex(salt);
        String hashHex = sha256(saltHex + password);

        return saltHex + "$" + hashHex;
    }

    /** 입력한 비밀번호가 저장된 "솔트$해시"와 일치하는지 검증 */
    public static boolean verify(String password, String stored) {
        if (stored == null || !stored.contains("$")) {
            return false;
        }

        String[] parts = stored.split("\\$", 2);
        String saltHex = parts[0];
        String expected = parts[1];

        return sha256(saltHex + password).equals(expected);
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
