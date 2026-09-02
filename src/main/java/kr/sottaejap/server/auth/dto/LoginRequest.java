package kr.sottaejap.server.auth.dto;

import jakarta.validation.constraints.NotNull;
import kr.sottaejap.server.common.enums.AuthProvider;

/**
 * POST /auth/login (05 #1). 스캐폴딩 단계에서는 LOCAL(데모 계정)만 받는다.
 */
public record LoginRequest(@NotNull AuthProvider provider) {
}
