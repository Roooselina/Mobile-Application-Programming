package com.example.aitonechanger.data;

import java.util.List;

/**
 * Builds the system prompt for tone correction.
 *
 * Inputs come from the selected prompt (recipient name + tone keywords +
 * persona + must-remember info) and from app settings (conversion strength,
 * response length, optional user profile context, forbidden expressions).
 *
 * Keywords and persona are treated as permanent constraints that must be
 * applied to every answer in the conversation and never dropped. When the
 * situation calls for it, the output follows a standard Korean formal
 * message/letter structure (greeting, body, closing, signature).
 *
 * The model must output only the resulting message, no preamble or markdown.
 */
public final class TonePromptBuilder {

    private TonePromptBuilder() {
    }

    public static String build(
            String recipient,
            List<String> keywords,
            String persona,       // 이 프롬프트(대화) 전용 페르소나
            String memoryInfo,    // 반드시 반영할 정보/규칙
            int strength,         // AppPrefs.STRENGTH_*
            int length,           // AppPrefs.LENGTH_*
            String profileName,
            String affiliation,
            String position,
            String forbidden
    ) {
        String recipientLine = (recipient == null || recipient.trim().isEmpty())
                ? "지정되지 않음 (일반적인 상대로 가정)"
                : recipient.trim();

        String keywordLine;
        boolean hasKeywords = keywords != null && !keywords.isEmpty();
        if (!hasKeywords) {
            keywordLine = "지정된 키워드 없음 (자연스럽고 무난한 말투)";
        } else {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(keywords.get(i));
            }
            keywordLine = sb.toString();
        }

        String strengthLine = (strength == 0)
                ? "원문 최대 유지 - 사용자가 쓴 표현과 단어를 최대한 살리고, 말투와 예의에 필요한 최소한만 손본다."
                : "균형 - 의미는 보존하되 지정된 대상과 말투에 맞게 표현을 자연스럽게 다듬는다.";

        String lengthLine;
        switch (length) {
            case 0:
                lengthLine = "짧게 선호 - 되도록 간결하게 핵심 위주로. 단, 내용상 꼭 필요한 정보나 격식은 생략하지 않는다.";
                break;
            case 2:
                lengthLine = "자세하게 선호 - 맥락과 부연, 인사와 형식을 충분히 갖춘다. 내용이 많으면 매우 길게 써도 좋다.";
                break;
            case 1:
            default:
                lengthLine = "유동적 - 길이를 내용에 맞춰 자연스럽게 정한다. 간단한 내용은 짧게, 복잡하거나 분량/격식이 필요한 내용은 충분히 길게.";
                break;
        }

        String personaBlock = "";
        if (notBlank(persona)) {
            personaBlock = "\n[나에 대한 정보 - 이 대화에서 '나(작성자)'의 고정 정체성 (절대 잊지 말 것)]\n"
                    + "아래는 이 대화에서 글쓴이 본인을 나타내는 정보다. 자기소개나 서명이 필요하면 반드시 이 정보를 그대로 사용하고, 대화가 길어져도 절대 바뀌거나 잊혀서는 안 된다. 이 정체성은 이 대화에만 적용한다.\n"
                    + persona.trim() + "\n";
        }

        String memoryBlock = "";
        if (notBlank(memoryInfo)) {
            memoryBlock = "\n[반드시 반영할 정보 - 모든 결과에 예외 없이, 끝까지 적용]\n"
                    + memoryInfo.trim() + "\n";
        }

        StringBuilder profileBlock = new StringBuilder();
        if (notBlank(profileName) || notBlank(affiliation) || notBlank(position)) {
            profileBlock.append("\n[작성자 정보 - 서명/자기소개에 활용, 없으면 무시]\n");
            if (notBlank(profileName)) {
                profileBlock.append("이름: ").append(profileName.trim()).append("\n");
            }
            if (notBlank(affiliation)) {
                profileBlock.append("소속: ").append(affiliation.trim()).append("\n");
            }
            if (notBlank(position)) {
                profileBlock.append("직책: ").append(position.trim()).append("\n");
            }
        }

        String forbiddenBlock = "";
        if (notBlank(forbidden)) {
            forbiddenBlock = "\n[금지 표현 - 아래 표현은 결과에 절대 사용하지 않는다]\n"
                    + forbidden.trim() + "\n";
        }

        String formalBlock = "\n[형식 규칙 - 격식이 필요할 때]\n"
                + "대상이 교수, 상사, 공식 기관 등 격식이 필요한 상대이거나, 입력이 부탁/요청/사과/공지/문의처럼 격식을 요하는 경우, 한국에서 통용되는 격식 있는 메시지/이메일 형식을 갖춘다. 아래 구성을 따른다.\n"
                + "1) 호칭: 대화 대상과 직책에 맞춰 'OOO께' 또는 'OOO 교수님께'처럼 시작한다. 이름은 [대화 대상]을 사용하고, 모르면 직책/관계에 맞는 호칭만 쓰며 이름을 지어내지 않는다.\n"
                + "2) 첫인사: '안녕하세요.'로 시작하고, 상황과 계절에 맞는 짧은 안부 한 마디를 자연스럽게 덧붙인다(억지로 넣지 않는다).\n"
                + "3) 본론: 용건을 명확하고 공손하게 전달한다. 사용자가 적은 내용만 사용하고 새로운 사실을 더하지 않는다.\n"
                + "4) 맺음말: '확인해 주시면 감사하겠습니다.', '잘 부탁드립니다.'처럼 정중히 마무리하고 '감사합니다.'로 끝맺는다.\n"
                + "5) 서명: [나에 대한 정보]에 정체성 정보가 있으면 그것을 우선 사용해 'OOO 올림' 또는 'OOO 드림' 형태로 서명한다. 없으면 [작성자 정보]의 이름을 사용하고, 둘 다 없으면 서명은 생략한다.\n"
                + "참고 형식:\n"
                + "OOO께,\n\n안녕하세요. 아침 날씨가 추우니 건강 유의하시기 바랍니다.\n(본론 내용)\n확인해 주시면 감사하겠습니다. 감사합니다.\n\nOOO 올림\n"
                + "단, 친구나 동료처럼 격식이 필요 없는 상대이거나 짧은 메시지면 위 형식을 강요하지 말고 자연스러운 단문으로 작성한다.\n";

        return "당신은 한국어 말투 교정 전용 도구이다. 사용자가 입력한 문장을 지정된 대화 대상과 말투에 맞게 변환하는 것이 유일한 역할이다.\n"
                + "\n"
                + "[대화 대상]\n"
                + recipientLine + "\n"
                + "\n"
                + "[말투 키워드 - 모든 답변에 항상 적용, 대화 중 절대 누락 금지]\n"
                + keywordLine + "\n"
                + personaBlock
                + memoryBlock
                + "\n"
                + "[변환 강도]\n"
                + strengthLine + "\n"
                + "\n"
                + "[응답 길이]\n"
                + lengthLine + "\n"
                + profileBlock
                + forbiddenBlock
                + formalBlock
                + "\n"
                + "[출력 규칙 - 반드시 모두 준수]\n"
                + "1. 변환된 메시지 본문만 출력한다. \"네, 도와드리겠습니다\", \"교정 결과는 다음과 같습니다\" 같은 안내 문구를 금지한다.\n"
                + "2. 마크다운, 코드 블록, 결과를 따옴표로 감싸는 행위를 금지한다. 결과 자체만 출력한다.\n"
                + "3. 사용자 입력에 명시되지 않은 정보(날짜, 장소, 인명, 수치 등)를 새로 지어내지 않는다.\n"
                + "4. 사용자 입력에 담긴 모든 의미는 손실 없이 보존한다. 핵심 정보를 누락하지 않는다.\n"
                + "5. 말투 키워드와 [나에 대한 정보]는 이 대화의 절대 규칙이다. 대화가 길어져도 매 답변에 빠짐없이 적용하며 절대 잊지 않는다.\n"
                + "6. 대화 일관성: 이전 대화에서 사용한 호칭, 말끝(예: ~요/~습니다), 자기소개 방식, 서명을 그대로 유지한다. 같은 상대에게 호칭이나 말투가 갑자기 바뀌면 안 된다.\n"
                + "6. 이전 대화 이력이 있으면 그 어조와 표현 방식에 일관되게 맞춘다.\n"
                + "7. 사용자가 이전 결과를 수정한 메시지가 있으면 그 표현 방식을 다음 변환의 기준으로 삼는다.\n"
                + "8. 이미 적절한 입력이어도 어색한 표현, 맞춤법, 띄어쓰기 정도는 다듬는다.\n"
                + "9. 비속어가 포함되면 의미는 보존하되 지정된 말투에 맞게 정제한다.\n"
                + "10. 격식이 필요한 상황이면 위 [형식 규칙]의 구성(호칭/인사/본론/맺음말/서명)을 갖추고, 일반 메시지면 단순 메시지 형식으로 출력한다.\n"
                + "11. 어떤 경우에도 메타 발언(\"이렇게 교정했습니다\" 등)을 추가하지 않는다.\n"
                + "12. 답변 길이는 내용에 맞춰 유동적으로 정한다. 한두 문장이면 충분한 경우 짧게, 긴 글이나 격식 있는 편지가 필요한 경우 충분히 길게 쓴다. 인위적인 길이 제한 없이 내용에 필요한 만큼 쓴다.\n";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
