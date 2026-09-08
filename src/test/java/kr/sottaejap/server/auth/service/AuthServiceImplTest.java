package kr.sottaejap.server.auth.service;

import kr.sottaejap.server.auth.dto.LoginRequest;
import kr.sottaejap.server.auth.dto.LoginResponse;
import kr.sottaejap.server.auth.exception.AuthErrorCode;
import kr.sottaejap.server.auth.jwt.AccessToken;
import kr.sottaejap.server.auth.jwt.JwtTokenProvider;
import kr.sottaejap.server.auth.kakao.KakaoOAuthClient;
import kr.sottaejap.server.auth.kakao.KakaoProfile;
import kr.sottaejap.server.auth.kakao.KakaoProperties;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.common.exception.ErrorCode;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * POST /auth/login 분기 (05 §2). 카카오 첫 로그인 = 사용자 생성, 재로그인 = 기존 행, 입력 오류는 카카오 호출 전에 거른다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String REDIRECT_URI = "http://localhost:5173/auth/callback";
    private static final KakaoProfile PROFILE = new KakaoProfile("5076490331", "닉네임", "user@example.com");

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        KakaoProperties properties = new KakaoProperties("k", "s", List.of(REDIRECT_URI),
                "https://kauth.kakao.com/oauth/token", "https://kapi.kakao.com/v2/user/me", 5000);
        authService = new AuthServiceImpl(userRepository, jwtTokenProvider, kakaoOAuthClient, properties,
                true, "demo@sottaejap.kr");
    }

    @Test
    void 카카오_첫_로그인은_사용자를_만들고_토큰을_준다() {
        when(kakaoOAuthClient.fetchProfile("code", REDIRECT_URI)).thenReturn(PROFILE);
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "5076490331")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 42L));
        when(jwtTokenProvider.issueAccessToken(42L)).thenReturn(new AccessToken("jwt", Instant.parse("2026-09-08T09:00:00Z")));

        LoginResponse response = authService.login(new LoginRequest(AuthProvider.KAKAO, "code", REDIRECT_URI));

        assertEquals("jwt", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals("2026-09-08T18:00+09:00", response.expiresAt().toString());
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertEquals(AuthProvider.KAKAO, saved.getValue().getAuthProvider());
        assertEquals("5076490331", saved.getValue().getProviderUserId());
        assertEquals("닉네임", saved.getValue().getNickname());
        assertEquals("user@example.com", saved.getValue().getEmail());
        assertEquals(1, saved.getValue().getRetrospectDelayDays());
        assertFalse(saved.getValue().isOnboardingCompleted());
    }

    @Test
    void 카카오_재로그인은_기존_사용자를_쓴다() {
        User existing = withId(User.social(AuthProvider.KAKAO, "5076490331", "옛닉", null), 7L);
        when(kakaoOAuthClient.fetchProfile("code", REDIRECT_URI)).thenReturn(PROFILE);
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "5076490331")).thenReturn(Optional.of(existing));
        when(jwtTokenProvider.issueAccessToken(7L)).thenReturn(new AccessToken("jwt", Instant.EPOCH));

        authService.login(new LoginRequest(AuthProvider.KAKAO, "code", REDIRECT_URI));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void 이메일_없는_카카오_프로필도_사용자가_된다() {
        when(kakaoOAuthClient.fetchProfile("code", REDIRECT_URI)).thenReturn(new KakaoProfile("9", "닉", null));
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "9")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> withId(invocation.getArgument(0), 1L));
        when(jwtTokenProvider.issueAccessToken(anyLong())).thenReturn(new AccessToken("jwt", Instant.EPOCH));

        authService.login(new LoginRequest(AuthProvider.KAKAO, "code", REDIRECT_URI));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        assertNull(saved.getValue().getEmail());
    }

    @Test
    void 카카오인데_code가_없으면_INVALID_INPUT이고_카카오를_부르지_않는다() {
        assertErrorCode(CommonErrorCode.INVALID_INPUT,
                () -> authService.login(new LoginRequest(AuthProvider.KAKAO, " ", REDIRECT_URI)));
        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    void redirectUri가_허용_목록_밖이면_INVALID_INPUT() {
        assertErrorCode(CommonErrorCode.INVALID_INPUT,
                () -> authService.login(new LoginRequest(AuthProvider.KAKAO, "code", "http://evil.example/callback")));
        assertErrorCode(CommonErrorCode.INVALID_INPUT,
                () -> authService.login(new LoginRequest(AuthProvider.KAKAO, "code", null)));
        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    void 카카오_오류는_그대로_올라간다() {
        when(kakaoOAuthClient.fetchProfile(anyString(), anyString()))
                .thenThrow(new BusinessException(AuthErrorCode.OAUTH_CODE_INVALID));

        assertErrorCode(AuthErrorCode.OAUTH_CODE_INVALID,
                () -> authService.login(new LoginRequest(AuthProvider.KAKAO, "used", REDIRECT_URI)));
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시_첫_로그인에서_진_요청도_승자의_사용자로_로그인된다() {
        User winner = withId(User.social(AuthProvider.KAKAO, "5076490331", "닉네임", "user@example.com"), 42L);
        when(kakaoOAuthClient.fetchProfile("code", REDIRECT_URI)).thenReturn(PROFILE);
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "5076490331"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(winner));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_provider"));
        when(jwtTokenProvider.issueAccessToken(42L)).thenReturn(new AccessToken("jwt", Instant.EPOCH));

        LoginResponse response = authService.login(new LoginRequest(AuthProvider.KAKAO, "code", REDIRECT_URI));

        assertEquals("jwt", response.accessToken());
        verify(userRepository, times(2)).findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "5076490331");
    }

    @Test
    void 충돌인데_재조회도_비면_예외를_삼키지_않는다() {
        when(kakaoOAuthClient.fetchProfile("code", REDIRECT_URI)).thenReturn(PROFILE);
        when(userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, "5076490331")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("uq_users_provider"));

        assertThrows(DataIntegrityViolationException.class,
                () -> authService.login(new LoginRequest(AuthProvider.KAKAO, "code", REDIRECT_URI)));
        verifyNoInteractions(jwtTokenProvider);
    }

    @Test
    void login은_트랜잭션_경계_밖이다() throws NoSuchMethodException {
        // 삽입이 충돌하면 그 트랜잭션은 rollback-only가 된다. 승자 재조회는 새 트랜잭션이어야 하므로 login을 묶지 않는다.
        assertNull(AuthServiceImpl.class.getAnnotation(Transactional.class));
        assertNull(AuthServiceImpl.class.getMethod("login", LoginRequest.class).getAnnotation(Transactional.class));
    }

    @Test
    void 데모_계정_로그인은_그대로_동작한다() {
        User demo = withId(User.social(AuthProvider.LOCAL, null, "데모 사용자", "demo@sottaejap.kr"), 1L);
        when(userRepository.findByEmailAndAuthProvider("demo@sottaejap.kr", AuthProvider.LOCAL)).thenReturn(Optional.of(demo));
        when(jwtTokenProvider.issueAccessToken(1L)).thenReturn(new AccessToken("jwt", Instant.EPOCH));

        LoginResponse response = authService.login(new LoginRequest(AuthProvider.LOCAL, null, null));

        assertEquals("jwt", response.accessToken());
        verifyNoInteractions(kakaoOAuthClient);
    }

    @Test
    void 네이버_구글은_UNSUPPORTED_PROVIDER() {
        assertErrorCode(AuthErrorCode.UNSUPPORTED_PROVIDER,
                () -> authService.login(new LoginRequest(AuthProvider.NAVER, null, null)));
        assertErrorCode(AuthErrorCode.UNSUPPORTED_PROVIDER,
                () -> authService.login(new LoginRequest(AuthProvider.GOOGLE, null, null)));
    }

    private static User withId(User user, long id) {
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static void assertErrorCode(ErrorCode expected, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, exception.getErrorCode());
    }
}
