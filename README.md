# AI 오늘의 말투 변환기

상대와 상황에 맞게 한국어 문장을 변환하는 Android 앱입니다.
대상별 말투 설정과 대화 이력을 저장하고, Claude API에 함께 전달합니다.

- 순천향대학교 스마트폰 애플리케이션 프로그래밍 기말 프로젝트 · 2026
- 개발 기간: 약 3개월 · 팀원 3명
- 사용 기술: Java, SQLite, Retrofit, OkHttp, Claude API

## 담당 업무

| 팀원 | 담당 |
|---|---|
| 이은정 | Android 개발, DB, 인증, 대화 로직, API 연동 |
| 박가영 | UI/UX 디자인 |
| 조한빈 | API 연동, 프롬프트 작성, 기능 개선 |

## 구현 내용

- SQLite에 계정별 프롬프트와 대화 이력, 응답 버전을 저장했습니다.
- 이메일 인증과 비밀번호 재설정 흐름을 구현했습니다.
- Retrofit과 OkHttp로 말투 설정·대화 이력을 포함한 변환 요청을 처리했습니다.
- 응답 재생성과 직접 수정한 내용을 별도 버전으로 저장했습니다.
- 입력 종료 1.2초 후 미리보기를 요청하고, 전송 시 미리보기 결과를 재사용하도록 구성했습니다.
- API 오류를 사용자 안내 메시지로 표시했습니다.

## 한계와 개선할 점

현재는 Android 클라이언트에서 API를 직접 호출합니다. local.properties를 Git에서 제외하더라도 빌드된 앱의 BuildConfig 값은 추출될 수 있으므로, 배포 전 서버 프록시와 서버 측 인증이 필요합니다.
응답 피드백은 저장 기능이며, 모델 재학습이나 개인화 학습으로 연결되어 있지는 않습니다.
미리보기 재사용에 따른 비용·응답 시간 개선율은 별도로 측정하지 않았습니다.

아래는 실행 방법과 상세 기능, 데이터 구조입니다.

---

## Security notice

local.properties contains credentials (API key and SMTP password) and must NEVER be committed.
It is listed in .gitignore by default. Before doing anything else:

1. Copy local.properties.example to local.properties.
2. Fill in your own API key and SMTP credentials.
3. Confirm that local.properties does NOT appear in git status before pushing.

If you have accidentally committed local.properties at any point, revoke your credentials
immediately and generate new ones before continuing.

---

## Overview

The core problem this app solves: existing AI tools require you to explain the recipient and
context every time, and the generated result must be manually copied into a separate messaging
app. This app pre-stores a per-contact prompt configuration, so you only type what you want
to say. The AI converts it and you can copy or share the result without leaving the app.

Key differentiators over using a general AI chatbot:

- Prompt configuration is saved per contact. You set the relationship, tone keywords, and
  identity information once. Every subsequent conversation with that contact reuses the same
  configuration.
- The full workflow lives in one screen: input, AI conversion, preview, regenerate, copy, share.
- Conversation history is preserved and used as context, so the AI maintains consistent
  salutations, tone endings, and signatures across turns.

---

## Features

### Account
- Email verification on signup: 6-digit code sent by SMTP, valid for 5 minutes, single-use.
  Changing the email address invalidates the current code.
- Password stored as SHA-256 hash with a per-user random salt.
- Password reset via email verification.
- Per-account data isolation: each account has its own prompts and conversation history.

### Prompt management
- Two-step prompt creation: (1) contact name, group, and self-description; (2) up to 5 tone
  keywords from a list of 20 defaults, with support for custom keywords.
- Self-description fallback chain: prompt field -> settings profile -> account name.
- Favorites and group-based sections in the sidebar.
- Swipe from left edge to open/close sidebar.

### Tone conversion (main chat screen)
- Input is sent to Claude with the full system prompt (contact, keywords, self-description,
  memory notes) and the conversation history. The model decides whether to apply formal
  Korean email structure (salutation, body, closing, signature) based on context.
- Conversation history is preserved across app restarts (SQLite).
- Consistent salutations, tone endings, and signatures within a conversation.
- Multiple versions per message: swipe left on an AI response to generate another version;
  navigate between versions with the arrow controls.
- Long-press an AI response to edit it manually; the edit is saved as a new version without
  an API call.
- Thumbs up / thumbs down feedback stored per message version.
- Real-time preview (optional): conversion runs 1.2 seconds after you stop typing and appears
  above the input field. If you send while the preview is visible, the cached result is used
  and no additional API call is made.
- Draft auto-save per prompt: unsaved input is restored when you return to a conversation.
- Background conversion: navigating away while a conversion is running does not cancel it.
  The result is saved and a notification is shown on completion.
- Recent input history: last 10 inputs per conversation, accessible from the input field.

### Search
- Unified search across prompt names and conversation content.
- Tapping a result navigates directly to the relevant conversation.

### Settings
- AI settings: conversion strength (preserve original wording / balanced), response length
  (short / medium / detailed), real-time preview toggle.
- User profile: name, affiliation, role. Used as default self-description when the prompt
  field is blank.
- Blocked expressions: words or phrases that the model will not include in results.
- Display mode: system / light / dark. Font size: 4 levels.
  Changes apply immediately without restarting the app.

---

## Architecture

```
app/src/main/java/com/example/aitonechanger/
|
|-- BaseActivity.java               shared theme, font scale, system bar inset handling
|-- Session.java                    in-memory login session
|-- AppPrefs.java                   SharedPreferences wrapper (theme, font, AI settings)
|-- PasswordUtil.java               SHA-256 + salt hashing
|-- EmailVerifier.java              SMTP-based verification code generation and validation
|-- DatabaseHelper.java             SQLiteOpenHelper: users, prompts, keywords, chat_messages,
|                                   message_versions tables
|
|-- data/
|   |-- api/
|   |   |-- GatewayApi.java         Retrofit interface for the Anthropic Messages endpoint
|   |   `-- RetrofitClient.java     OkHttp + Retrofit setup, header injection, logging
|   |-- model/
|   |   |-- AnthropicRequest.java   request body model
|   |   `-- AnthropicResponse.java  response body model
|   |-- TonePromptBuilder.java      builds the structured Korean system prompt from
|   |                               prompt config + app settings
|   `-- ToneChatRepository.java     orchestrates conversion requests and callbacks
|
|-- SplashActivity.java
|-- LoginActivity.java
|-- SignupActivity.java
|-- MainActivity.java               main chat screen
|-- PromptCreateActivity.java       step 1 of prompt creation
|-- PromptCreateKeywordActivity.java  step 2 of prompt creation
|-- PromptEditActivity.java
|-- PromptData.java                 data classes: Prompt, ChatMessage
|-- SearchActivity.java
|-- ProfileActivity.java
|-- ProfileEditActivity.java
|-- ProfileDefaults.java
|-- PasswordChangeActivity.java
|-- SettingsActivity.java
`-- KeywordEditorHelper.java        keyword chip management helper
```

### Data storage

SQLite tables:

| Table | Key columns |
|---|---|
| users | id, name, email (unique), nickname, password (salt$hash) |
| prompts | id, user_id, title, keywords (CSV), persona, memory_info, grp, favorite |
| keywords | id, user_id, keyword |
| chat_messages | id, user_id, prompt_id, sender, message, time_text, current_version, feedback, created_at |
| message_versions | id, message_id, content |

Light preferences (theme, font, AI settings) are stored in SharedPreferences.

### API integration

The app calls the Anthropic Messages API directly from the Android client.

- Authentication: x-api-key header, anthropic-version: 2023-06-01
- System prompt: passed in the top-level system field, not inside the messages array
- Model: claude-sonnet-4-6 (configurable via local.properties)
- max_tokens: 4096 (short) / 8192 (medium) / 16000 (detailed), based on settings
- temperature: 0.3

HTTP 4xx/5xx error codes (401, 400, 429, 529) are mapped to user-readable Korean messages.

API key and SMTP credentials are loaded from local.properties at build time via
BuildConfig constants. They are never hardcoded in source files.

---

## Tech stack

| Component | Library / version |
|---|---|
| Language | Java |
| Min SDK | 26 (Android 8.0) |
| Target / Compile SDK | 35 |
| Network | Retrofit 2.11.0 + OkHttp 4.12.0 + logging-interceptor |
| Serialization | Gson (converter-gson) |
| Local storage | SQLite (SQLiteOpenHelper) + SharedPreferences |
| Email | JavaMail for Android (android-mail 1.6.7, android-activation 1.6.7) |
| AI engine | Anthropic Claude Messages API |

---

## Setup

### Prerequisites
- Android Studio (Hedgehog or later recommended)
- Android SDK 35
- Java 11

### 1. Clone and open

```
git clone https://github.com/Roooselina/Mobile-Application-Programming.git
```

Open the `AI_Tone_Converter` folder in Android Studio.

### 2. Configure credentials

Copy the example file:

```
cp local.properties.example local.properties
```

Edit local.properties:

```
ANTHROPIC_API_KEY=your_anthropic_api_key_here
GATEWAY_BASE_URL=https://api.anthropic.com/
GATEWAY_MODEL=claude-sonnet-4-6

SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_EMAIL=your_gmail_address@gmail.com
SMTP_PASSWORD=your_16_char_app_password
```

To get an Anthropic API key: https://console.anthropic.com

For SMTP, generate a Gmail App Password (requires 2-Step Verification):
Google Account > Security > 2-Step Verification > App passwords

If SMTP settings are left blank, the app runs in demo mode: the verification code is
displayed on screen instead of being sent by email.

### 3. Build and run

Run the app on a device or emulator from Android Studio (Run > Run 'app').

---

## Files excluded from version control

The following are in .gitignore and must not be committed:

```
local.properties          contains API key and SMTP password
.gradle/                  Gradle cache
app/build/                compiled output and APK files
*.apk
```

---

## Team roles

| Role | Member |
|---|---|
| UI/UX design | Park Gayoung |
| Android development (DB, auth, chat logic, API integration) | Lee Eunjung |
| API integration, prompt engineering, feature refinement | Jo Hanbin |

