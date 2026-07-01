package com.example.aitonechanger;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.security.SecureRandom;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * 이메일 인증코드 발송/검증 도우미.
 *
 * 동작 방식:
 *   1) requestCode(email, ...) 호출 시 6자리 코드를 생성하고 유효시간(5분)을 기록한다.
 *   2) local.properties 에 SMTP 설정(SMTP_EMAIL, SMTP_PASSWORD)이 있으면
 *      JavaMail 로 실제 인증 메일을 보낸다. (예: Gmail + 앱 비밀번호)
 *   3) SMTP 설정이 없으면 "데모 모드"로 동작한다 — 메일은 보내지 않고
 *      생성된 코드를 콜백으로 돌려줘서 화면(다이얼로그)에 표시한다.
 *      과제 시연/로컬 테스트용이며, 실제 배포 시에는 SMTP 또는 서버 인증을 써야 한다.
 *   4) verify(email, code) 로 입력 코드를 검증한다. (이메일이 바뀌면 무효)
 *
 * SMTP 설정 방법 (local.properties):
 *   SMTP_HOST=smtp.gmail.com
 *   SMTP_PORT=587
 *   SMTP_EMAIL=내주소@gmail.com
 *   SMTP_PASSWORD=구글 앱 비밀번호 16자리
 */
public final class EmailVerifier {

    private static final String TAG = "EmailVerifier";
    private static final long CODE_VALID_MILLIS = 5 * 60 * 1000L; // 5분

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // 마지막으로 발급한 코드 상태 (앱 프로세스 메모리에만 보관)
    private static String pendingEmail = null;
    private static String pendingCode = null;
    private static long pendingExpiresAt = 0L;

    private EmailVerifier() {
    }

    public interface SendCallback {
        /** 실제 메일 발송에 성공했을 때 */
        void onSent();

        /** SMTP 미설정: 데모 모드. 코드가 화면에 표시되도록 돌려준다. */
        void onDemoMode(String code);

        void onError(String message);
    }

    /** SMTP가 설정되어 실제 메일을 보낼 수 있는 상태인지 */
    public static boolean isSmtpConfigured() {
        return notBlank(BuildConfig.SMTP_EMAIL) && notBlank(BuildConfig.SMTP_PASSWORD);
    }

    /** 인증코드를 생성하고 발송(또는 데모 모드 반환)한다. 콜백은 메인 스레드에서 호출된다. */
    public static void requestCode(String email, SendCallback callback) {
        if (email == null || !email.contains("@")) {
            mainHandler.post(() -> callback.onError("올바른 이메일 형식이 아닙니다."));
            return;
        }

        final String target = email.trim();
        final String code = generateCode();

        pendingEmail = target;
        pendingCode = code;
        pendingExpiresAt = System.currentTimeMillis() + CODE_VALID_MILLIS;

        if (!isSmtpConfigured()) {
            // 데모 모드: 메일 발송 없이 코드를 화면에 보여준다.
            mainHandler.post(() -> callback.onDemoMode(code));
            return;
        }

        executor.execute(() -> {
            try {
                sendMail(target, code);
                mainHandler.post(callback::onSent);
            } catch (Exception e) {
                Log.e(TAG, "인증 메일 발송 실패", e);
                mainHandler.post(() -> callback.onError(
                        "인증 메일 발송에 실패했습니다. SMTP 설정을 확인해주세요."));
            }
        });
    }

    /** 입력한 코드가 유효한지 검증한다. 성공 시 코드 상태를 소모(초기화)한다. */
    public static boolean verify(String email, String inputCode) {
        if (pendingEmail == null || pendingCode == null) return false;
        if (email == null || !pendingEmail.equalsIgnoreCase(email.trim())) return false;
        if (System.currentTimeMillis() > pendingExpiresAt) return false;
        if (inputCode == null || !pendingCode.equals(inputCode.trim())) return false;

        // 1회 사용 후 무효화
        pendingEmail = null;
        pendingCode = null;
        pendingExpiresAt = 0L;
        return true;
    }

    /** 이메일이 바뀌는 등 인증을 무효화해야 할 때 호출 */
    public static void invalidate() {
        pendingEmail = null;
        pendingCode = null;
        pendingExpiresAt = 0L;
    }

    private static String generateCode() {
        SecureRandom random = new SecureRandom();
        int n = random.nextInt(1_000_000); // 0 ~ 999999
        return String.format("%06d", n);
    }

    private static void sendMail(String to, String code) throws Exception {
        final String host = notBlank(BuildConfig.SMTP_HOST) ? BuildConfig.SMTP_HOST : "smtp.gmail.com";
        final String port = notBlank(BuildConfig.SMTP_PORT) ? BuildConfig.SMTP_PORT : "587";
        final String from = BuildConfig.SMTP_EMAIL;
        final String password = BuildConfig.SMTP_PASSWORD;

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(from, password);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(to));
        message.setSubject("[AI 말투 변환기] 이메일 인증코드");
        message.setText("안녕하세요.\n\nAI 말투 변환기 이메일 인증코드는 [" + code + "] 입니다.\n"
                + "인증코드는 5분간 유효합니다.\n\n본인이 요청하지 않았다면 이 메일을 무시해주세요.");

        Transport.send(message);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
