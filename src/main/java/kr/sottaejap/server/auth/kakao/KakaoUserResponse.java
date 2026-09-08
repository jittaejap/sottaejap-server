package kr.sottaejap.server.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.util.StringUtils;

/** kapi.kakao.com/v2/user/me 응답 중 서버가 읽는 부분. 9/7 실측 키 기준 (05 §2). */
@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoUserResponse(Long id, Properties properties, @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Properties(String nickname) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record KakaoAccount(String email,
                        @JsonProperty("has_email") Boolean hasEmail,
                        @JsonProperty("email_needs_agreement") Boolean emailNeedsAgreement,
                        Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Profile(String nickname) {
    }

    KakaoProfile toProfile() {
        String nickname = kakaoAccount != null && kakaoAccount.profile() != null
                && StringUtils.hasText(kakaoAccount.profile().nickname())
                ? kakaoAccount.profile().nickname()
                : properties != null ? properties.nickname() : null;
        String email = kakaoAccount != null
                && Boolean.TRUE.equals(kakaoAccount.hasEmail())
                && !Boolean.TRUE.equals(kakaoAccount.emailNeedsAgreement())
                && StringUtils.hasText(kakaoAccount.email())
                ? kakaoAccount.email()
                : null;
        return new KakaoProfile(Long.toString(id), nickname, email);
    }
}
