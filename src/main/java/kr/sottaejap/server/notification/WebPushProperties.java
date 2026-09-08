package kr.sottaejap.server.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * VAPID 키쌍과 연락처 (07 §3 환경 변수). 키가 비어 있으면 Web Push는 꺼진 상태로 동작한다 —
 * 인앱 알림은 그대로 만들어지므로 데모가 멈추지 않는다.
 *
 * <p>키 생성 방법은 README "Web Push"를 보십시오. 형식이 틀리면 기동 시점에 실패한다.
 *
 * @param publicKey  base64url P-256 공개키 (클라이언트가 구독할 때 쓰는 applicationServerKey)
 * @param privateKey base64url P-256 개인키 — 로그·응답에 절대 싣지 않는다
 * @param subject    푸시 서비스가 문제 시 연락할 곳. `mailto:` 또는 https URL이어야 한다
 */
@ConfigurationProperties("notification.web-push")
public record WebPushProperties(String publicKey, String privateKey, String subject) {

    public boolean isConfigured() {
        return publicKey != null && !publicKey.isBlank()
                && privateKey != null && !privateKey.isBlank()
                && subject != null && !subject.isBlank();
    }
}
