package kr.sottaejap.server.auth.kakao;

import kr.sottaejap.server.auth.exception.AuthErrorCode;
import kr.sottaejap.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 카카오와 통신하는 유일한 지점 (E-55). 인가 코드 → 토큰 교환 → 프로필 조회.
 *
 * <p>토큰 엔드포인트의 400만 코드 문제(만료 · 재사용 · redirect_uri 불일치, 실측 KOE320)라 400 OAUTH_CODE_INVALID,
 * 그 외 실패(400 아닌 4xx · 5xx · 타임아웃 · 프로필 조회 실패)는 502 OAUTH_PROVIDER_ERROR로 바꾼다 (05 §2 실측 ③).
 */
@Component
public class KakaoOAuthClient {

    private final KakaoProperties properties;
    private final RestClient restClient;

    @Autowired
    public KakaoOAuthClient(KakaoProperties properties) {
        this(properties, RestClient.builder().requestFactory(requestFactory(properties)));
    }

    KakaoOAuthClient(KakaoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    private static JdkClientHttpRequestFactory requestFactory(KakaoProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));
        return factory;
    }

    /** 인가 코드를 카카오 프로필로 바꾼다. 카카오 액세스 토큰은 이 메서드 밖으로 나가지 않는다. */
    public KakaoProfile fetchProfile(String code, String redirectUri) {
        if (!properties.configured()) {
            throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
        }
        String accessToken = exchangeCode(code, redirectUri);
        return fetchUser(accessToken);
    }

    private String exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        try {
            KakaoTokenResponse token = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
            if (token == null || token.accessToken() == null) {
                throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return token.accessToken();
        } catch (HttpClientErrorException.BadRequest exception) {
            throw new BusinessException(AuthErrorCode.OAUTH_CODE_INVALID, exception);
        } catch (RestClientException exception) {
            // 401 · 403(앱 인증 · 권한) · 429(요청 제한)는 새 인가 코드를 받아도 풀리지 않는다. 코드 오류로 안내하면 로그인 루프가 된다.
            throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR, exception);
        }
    }

    private KakaoProfile fetchUser(String accessToken) {
        try {
            KakaoUserResponse user = restClient.get()
                    .uri(properties.userInfoUri())
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (user == null || user.id() == null) {
                throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
            }
            return user.toProfile();
        } catch (RestClientException exception) {
            throw new BusinessException(AuthErrorCode.OAUTH_PROVIDER_ERROR, exception);
        }
    }
}
