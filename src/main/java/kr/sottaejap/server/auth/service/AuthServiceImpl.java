package kr.sottaejap.server.auth.service;

import kr.sottaejap.server.auth.dto.LoginRequest;
import kr.sottaejap.server.auth.dto.LoginResponse;
import kr.sottaejap.server.auth.exception.AuthErrorCode;
import kr.sottaejap.server.auth.jwt.AccessToken;
import kr.sottaejap.server.auth.jwt.JwtTokenProvider;
import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;

/**
 * 데모 계정 폴백 (E-15). 카카오 OAuth2는 Spring Security OAuth2 Client로 별도 연결한다 (07 §10 리스크 1 스파이크).
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final boolean demoAccountEnabled;
    private final String demoAccountEmail;

    public AuthServiceImpl(UserRepository userRepository,
                           JwtTokenProvider jwtTokenProvider,
                           @Value("${auth.demo-account.enabled}") boolean demoAccountEnabled,
                           @Value("${auth.demo-account.email}") String demoAccountEmail) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.demoAccountEnabled = demoAccountEnabled;
        this.demoAccountEmail = demoAccountEmail;
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request.provider() != AuthProvider.LOCAL) {
            throw new BusinessException(AuthErrorCode.UNSUPPORTED_PROVIDER);
        }
        if (!demoAccountEnabled) {
            throw new BusinessException(AuthErrorCode.DEMO_ACCOUNT_DISABLED);
        }
        User demoUser = userRepository.findByEmailAndAuthProvider(demoAccountEmail, AuthProvider.LOCAL)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        AccessToken accessToken = jwtTokenProvider.issueAccessToken(demoUser.getId());
        return new LoginResponse(accessToken.value(), "Bearer", accessToken.expiresAt().atZone(SEOUL).toOffsetDateTime());
    }
}
