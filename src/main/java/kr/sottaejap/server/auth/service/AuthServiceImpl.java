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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

    /**
     * 트랜잭션으로 묶지 않는다. 첫 로그인 삽입이 경합에서 지면 그 트랜잭션은 롤백되고, 승자의 행을 다시 조회하려면
     * 새 트랜잭션이 필요하다 (registerKakaoUser). 로그인은 조회 · 삽입을 한꺼번에 되돌릴 이유도 없다.
     */
    @Override
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
                .orElseGet(() -> registerKakaoUser(profile));
    }

    /**
     * 첫 로그인 사용자 생성 (E-56). 같은 카카오 계정으로 두 요청이 동시에 들어오면 진 쪽이 V1 uq_users_provider 충돌을 받는다.
     * 이미 소비한 인가 코드로는 재시도할 수 없으므로, 진 쪽도 승자가 만든 행을 다시 조회해 로그인을 성사시킨다.
     * saveAndFlush의 트랜잭션은 충돌과 함께 롤백되고, 이어지는 조회는 그 밖에서 새 트랜잭션으로 실행된다.
     */
    private User registerKakaoUser(KakaoProfile profile) {
        try {
            return userRepository.saveAndFlush(
                    User.social(AuthProvider.KAKAO, profile.providerUserId(), profile.nickname(), profile.email()));
        } catch (DataIntegrityViolationException conflict) {
            return userRepository.findByAuthProviderAndProviderUserId(AuthProvider.KAKAO, profile.providerUserId())
                    .orElseThrow(() -> conflict);
        }
    }
}
