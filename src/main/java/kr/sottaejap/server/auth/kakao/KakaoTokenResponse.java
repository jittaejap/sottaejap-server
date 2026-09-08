package kr.sottaejap.server.auth.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** kauth.kakao.com/oauth/token 응답. 액세스 토큰은 프로필 조회에만 쓰고 저장·출력하지 않는다 (E-55). */
@JsonIgnoreProperties(ignoreUnknown = true)
record KakaoTokenResponse(@JsonProperty("access_token") String accessToken,
                          @JsonProperty("token_type") String tokenType) {
}
