package kr.sottaejap.server.auth.kakao;

import kr.sottaejap.server.auth.exception.AuthErrorCode;
import kr.sottaejap.server.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 카카오 응답을 흉내 내어 코드 교환 · 프로필 추출 · 오류 매핑을 확인한다 (05 §2 실측 ①~③).
 */
class KakaoOAuthClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URI = "https://kapi.kakao.com/v2/user/me";
    private static final String REDIRECT_URI = "http://localhost:5173/auth/callback";
    private static final String TOKEN_JSON = """
            {"token_type":"bearer","access_token":"kakao-access","expires_in":21599,"scope":"account_email profile_nickname"}
            """;
    private static final String USER_JSON = """
            {"id":5076490331,"connected_at":"2026-09-07T05:30:00Z",
             "properties":{"nickname":"속성닉"},
             "kakao_account":{"profile_nickname_needs_agreement":false,"profile":{"nickname":"프로필닉"},
                              "has_email":true,"email_needs_agreement":false,"is_email_valid":true,"is_email_verified":true,
                              "email":"user@example.com"}}
            """;
    private static final String USER_JSON_NO_EMAIL = """
            {"id":7,"properties":{"nickname":"속성닉"},
             "kakao_account":{"profile":{"nickname":"프로필닉"},"has_email":true,"email_needs_agreement":true}}
            """;
    private static final String KOE320_JSON = """
            {"error":"invalid_grant","error_description":"authorization code not found","error_code":"KOE320"}
            """;

    private MockRestServiceServer server;
    private KakaoOAuthClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new KakaoOAuthClient(properties("rest-api-key", "client-secret"), builder);
    }

    @Test
    void 코드를_교환하고_프로필을_읽는다() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE))
                .andExpect(content().formData(form("auth-code")))
                .andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access"))
                .andRespond(withSuccess(USER_JSON, MediaType.APPLICATION_JSON));

        KakaoProfile profile = client.fetchProfile("auth-code", REDIRECT_URI);

        assertEquals("5076490331", profile.providerUserId());
        assertEquals("프로필닉", profile.nickname(), "kakao_account.profile.nickname을 우선한다");
        assertEquals("user@example.com", profile.email());
        server.verify();
    }

    @Test
    void 이메일_동의가_없으면_email은_null이다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URI)).andRespond(withSuccess(USER_JSON_NO_EMAIL, MediaType.APPLICATION_JSON));

        KakaoProfile profile = client.fetchProfile("auth-code", REDIRECT_URI);

        assertEquals("7", profile.providerUserId());
        assertNull(profile.email());
    }

    @Test
    void 토큰_엔드포인트_400은_OAUTH_CODE_INVALID() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON).body(KOE320_JSON));

        assertErrorCode(AuthErrorCode.OAUTH_CODE_INVALID, () -> client.fetchProfile("used-code", REDIRECT_URI));
    }

    @Test
    void 토큰_엔드포인트의_400_아닌_4xx는_OAUTH_PROVIDER_ERROR() {
        // 요청 제한 · 앱 인증 실패는 새 인가 코드를 받아도 풀리지 않는다 — 코드 오류로 안내하면 클라이언트가 로그인 루프에 빠진다.
        assertTokenStatusMapsTo(HttpStatus.TOO_MANY_REQUESTS, AuthErrorCode.OAUTH_PROVIDER_ERROR);
        assertTokenStatusMapsTo(HttpStatus.UNAUTHORIZED, AuthErrorCode.OAUTH_PROVIDER_ERROR);
        assertTokenStatusMapsTo(HttpStatus.FORBIDDEN, AuthErrorCode.OAUTH_PROVIDER_ERROR);
    }

    @Test
    void 토큰_엔드포인트_5xx는_OAUTH_PROVIDER_ERROR() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertErrorCode(AuthErrorCode.OAUTH_PROVIDER_ERROR, () -> client.fetchProfile("auth-code", REDIRECT_URI));
    }

    @Test
    void 타임아웃은_OAUTH_PROVIDER_ERROR() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertErrorCode(AuthErrorCode.OAUTH_PROVIDER_ERROR, () -> client.fetchProfile("auth-code", REDIRECT_URI));
    }

    @Test
    void 프로필_조회_실패는_OAUTH_PROVIDER_ERROR() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(TOKEN_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_URI)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertErrorCode(AuthErrorCode.OAUTH_PROVIDER_ERROR, () -> client.fetchProfile("auth-code", REDIRECT_URI));
    }

    @Test
    void 키가_비어_있으면_카카오를_부르지_않고_OAUTH_PROVIDER_ERROR() {
        KakaoOAuthClient unconfigured = new KakaoOAuthClient(properties("", ""), RestClient.builder());

        assertErrorCode(AuthErrorCode.OAUTH_PROVIDER_ERROR, () -> unconfigured.fetchProfile("auth-code", REDIRECT_URI));
    }

    @Test
    void redirectUri_허용_목록을_검사한다() {
        KakaoProperties properties = properties("k", "s");

        assertEquals(true, properties.allowsRedirectUri(REDIRECT_URI));
        assertEquals(false, properties.allowsRedirectUri("http://evil.example/callback"));
        assertEquals(false, properties.allowsRedirectUri(null));
    }

    /** MockRestServiceServer는 기대 하나에 응답 하나라, 상태 코드마다 클라이언트를 새로 만든다. */
    private void assertTokenStatusMapsTo(HttpStatus status, AuthErrorCode expected) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        KakaoOAuthClient tokenClient = new KakaoOAuthClient(properties("rest-api-key", "client-secret"), builder);
        mockServer.expect(requestTo(TOKEN_URI)).andRespond(withStatus(status));

        assertErrorCode(expected, () -> tokenClient.fetchProfile("auth-code", REDIRECT_URI));
        mockServer.verify();
    }

    private static KakaoProperties properties(String clientId, String clientSecret) {
        return new KakaoProperties(clientId, clientSecret, List.of(REDIRECT_URI), TOKEN_URI, USER_URI, 5000);
    }

    private static org.springframework.util.MultiValueMap<String, String> form(String code) {
        org.springframework.util.LinkedMultiValueMap<String, String> form = new org.springframework.util.LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "rest-api-key");
        form.add("client_secret", "client-secret");
        form.add("redirect_uri", REDIRECT_URI);
        form.add("code", code);
        return form;
    }

    private static void assertErrorCode(AuthErrorCode expected, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, exception.getErrorCode());
    }
}
