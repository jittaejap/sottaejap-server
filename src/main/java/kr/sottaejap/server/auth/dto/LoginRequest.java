package kr.sottaejap.server.auth.dto;

import jakarta.validation.constraints.NotNull;
import kr.sottaejap.server.common.enums.AuthProvider;

/**
 * POST /auth/login (05 §2). LOCAL은 provider만, KAKAO는 code · redirectUri가 필수다 (E-55).
 * provider별 필수 여부는 서비스가 검사해 INVALID_INPUT으로 돌려준다.
 *
 * @param code        카카오가 클라이언트 콜백으로 돌려준 인가 코드 (KAKAO만)
 * @param redirectUri 인가 요청에 썼던 클라이언트 콜백 URL — auth.kakao.redirect-uris 목록에 있어야 한다 (KAKAO만)
 */
public record LoginRequest(@NotNull AuthProvider provider, String code, String redirectUri) {
}
