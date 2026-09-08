package kr.sottaejap.server.auth.kakao;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 07 §3 — KAKAO_CLIENT_ID · KAKAO_CLIENT_SECRET · KAKAO_REDIRECT_URIS (E-55).
 *
 * @param clientId     REST API 키. 클라이언트에도 공개되는 값
 * @param clientSecret 서버에만 둔다. 카카오 콘솔 "Client Secret 사용함"이면 토큰 교환에 필수
 * @param redirectUris 허용할 클라이언트 콜백 URL 목록. 카카오 콘솔에 등록한 값과 같아야 한다
 * @param tokenUri     kauth.kakao.com/oauth/token
 * @param userInfoUri  kapi.kakao.com/v2/user/me
 * @param timeoutMs    카카오 호출 1건의 읽기 타임아웃
 */
@ConfigurationProperties("auth.kakao")
public record KakaoProperties(String clientId, String clientSecret, List<String> redirectUris,
                              String tokenUri, String userInfoUri, long timeoutMs) {

    /** 키가 비어 있으면 카카오 로그인을 시도할 수 없다. CI·키 없는 로컬은 데모 계정만 쓴다. */
    public boolean configured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
    }

    public boolean allowsRedirectUri(String redirectUri) {
        return redirectUris != null && redirectUri != null && redirectUris.contains(redirectUri);
    }
}
