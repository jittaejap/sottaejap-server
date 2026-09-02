package kr.sottaejap.server.auth.dto;

import java.time.OffsetDateTime;

public record LoginResponse(String accessToken, String tokenType, OffsetDateTime expiresAt) {
}
