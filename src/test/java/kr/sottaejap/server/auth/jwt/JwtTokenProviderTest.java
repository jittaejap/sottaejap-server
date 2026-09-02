package kr.sottaejap.server.auth.jwt;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenProviderTest {

    private static final String SECRET = Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef0123456789abcdef".getBytes());
    private static final Instant NOW = Instant.parse("2026-09-02T09:00:00Z");

    private static JwtTokenProvider providerAt(Instant instant) {
        return new JwtTokenProvider(new JwtProperties(SECRET, "sottaejap", 3600), Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void issueAndParse_roundTrip() {
        JwtTokenProvider provider = providerAt(NOW);
        AccessToken token = provider.issueAccessToken(1L);

        AccessTokenClaims claims = provider.parseAccessToken(token.value());

        assertEquals(1L, claims.userId());
        assertEquals(NOW, claims.issuedAt());
        assertEquals(NOW.plus(Duration.ofHours(1)), claims.expiresAt());
    }

    @Test
    void parse_expiredToken_throws() {
        AccessToken token = providerAt(NOW).issueAccessToken(1L);
        JwtTokenProvider later = providerAt(NOW.plus(Duration.ofHours(2)));

        assertThrows(ExpiredJwtException.class, () -> later.parseAccessToken(token.value()));
    }

    @Test
    void parse_wrongIssuer_throws() {
        AccessToken token = new JwtTokenProvider(new JwtProperties(SECRET, "someone-else", 3600),
                Clock.fixed(NOW, ZoneOffset.UTC)).issueAccessToken(1L);

        assertThrows(JwtException.class, () -> providerAt(NOW).parseAccessToken(token.value()));
    }

    @Test
    void constructor_shortSecret_throws() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenProvider(new JwtProperties(shortSecret, "sottaejap", 3600)));
    }
}
