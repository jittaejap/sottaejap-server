package kr.sottaejap.server.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

/**
 * HS256 액세스 토큰 발급·검증. 05 §0 — `Authorization: Bearer <token>`.
 */
@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN_TYPE = "access";

    private final SecretKey signingKey;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Clock clock;
    private final JwtParser jwtParser;

    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenProvider(JwtProperties properties, Clock clock) {
        if (!StringUtils.hasText(properties.issuer())) {
            throw new IllegalArgumentException("auth.jwt.issuer must not be blank");
        }
        if (properties.accessTokenTtlSeconds() <= 0) {
            throw new IllegalArgumentException("auth.jwt.access-token-ttl-seconds must be positive");
        }
        this.signingKey = createSigningKey(properties.secret());
        this.issuer = properties.issuer();
        this.accessTokenTtl = Duration.ofSeconds(properties.accessTokenTtlSeconds());
        this.clock = clock;
        this.jwtParser = Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(issuer)
                .require(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .sig().clear().add(Jwts.SIG.HS256).and()
                .clock(() -> Date.from(clock.instant()))
                .build();
    }

    public AccessToken issueAccessToken(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String value = Jwts.builder()
                .issuer(issuer)
                .subject(Long.toString(userId))
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(TOKEN_TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
        return new AccessToken(value, expiresAt);
    }

    /**
     * 서명·만료·발급자·토큰 종류를 검증한다. 실패하면 {@link io.jsonwebtoken.JwtException}을 던진다.
     */
    public AccessTokenClaims parseAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Access token must not be blank");
        }
        Claims claims = jwtParser.parseSignedClaims(token).getPayload();
        if (!StringUtils.hasText(claims.getId()) || claims.getIssuedAt() == null || claims.getExpiration() == null) {
            throw new MalformedJwtException("Access token is missing required claims");
        }
        return new AccessTokenClaims(
                parseUserId(claims.getSubject()),
                claims.getId(),
                claims.getIssuedAt().toInstant(),
                claims.getExpiration().toInstant()
        );
    }

    private SecretKey createSigningKey(String base64Secret) {
        if (!StringUtils.hasText(base64Secret)) {
            throw new IllegalArgumentException("JWT_SECRET must not be blank — 생성: openssl rand -base64 48");
        }
        try {
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be a Base64-encoded key of at least 32 bytes", exception);
        }
    }

    private long parseUserId(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new NumberFormatException("User ID must be positive");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException("JWT subject must be a numeric user ID", exception);
        }
    }
}
