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
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;

/**
 * 로그인 2종 (05 §2). LOCAL = 데모 계정 폴백 (E-15), KAKAO = 인가 코드 교환 후 첫 로그인이면 사용자 생성 (E-55 · E-56).
 * 어느 쪽이든 클라이언트에는 우리 JWT만 내려간다.
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final KakaoOAuthClient kakaoOAuthClient;
    private final KakaoProperties kakaoProperties;
    private final boolean demoAccountEnabled;
    private final String demoAccountEmail;

    public AuthServiceImpl(UserRepository userRepository,
                           JwtTokenProvider jwtTokenProvider,
                           KakaoOAuthClient kakaoOAuthClient,
                           KakaoProperties kakaoProperties,
                           @Value("${auth.demo-account.enabled}") boolean demoAccountEnabled,
                           @Value("${auth.demo-account.email}") String demoAccountEmail) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.kakaoOAuthClient = kakaoOAuthClient;
        this.kakaoProperties = kakaoProperties;
        this.demoAccountEnabled = demoAccountEnabled;
        this.demoAccountEmail = demoAccountEmail;
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = switch (request.provider()) {
            case LOCAL -> demoUser();
            case KAKAO -> kakaoUser(request);
            default -> throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        };
        AccessToken accessToken = jwtTokenProvider.issueAccessToken(user.getId());
        return new LoginResponse(accessToken.value(), "Bearer", accessToken.expiresAt().atZone(SEOUL).toOffsetDateTime());
    }

    private User demoUser() {
        if (!demoAccountEnabled) {
            throw new BusinessException(AuthErrorCode.DEMO_ACCOUNT_DISABLED);
        }
        return userRepository.findByEmailAndAuthProvider(demoAccountEmail, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 카카오는 code · redirectUri가 필수이고 redirectUri는 허용 목록 안이어야 한다 (05 §2). 카카오 호출 전에 거른다. */
    private User kakaoUser(LoginRequest request) {
        if (!StringUtils.hasText(request.code()) || !kakaoProperties.allowsRedirectUri(request.redirectUri())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        KakaoProfile profile = kakaoOAuthClient.fetchProfile(request.code(), request.redirectUri());
        return userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, profile.providerUserId())
                .orElseGet(() -> userRepository.save(
                        User.social(AuthProvider.KAKAO, profile.providerUserId(), profile.nickname(), profile.email())));
    }
}
