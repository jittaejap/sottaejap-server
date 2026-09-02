package kr.sottaejap.server.auth.jwt;

import java.time.Instant;

public record AccessTokenClaims(long userId, String tokenId, Instant issuedAt, Instant expiresAt) {
}
