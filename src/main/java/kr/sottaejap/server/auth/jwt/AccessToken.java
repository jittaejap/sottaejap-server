package kr.sottaejap.server.auth.jwt;

import java.time.Instant;

public record AccessToken(String value, Instant expiresAt) {
}
