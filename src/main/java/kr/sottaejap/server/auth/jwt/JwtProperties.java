package kr.sottaejap.server.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param secret Base64로 인코딩한 32바이트 이상 HMAC 키
 * @param issuer iss 클레임
 * @param accessTokenTtlSeconds 액세스 토큰 수명(초)
 */
@ConfigurationProperties("auth.jwt")
public record JwtProperties(String secret, String issuer, long accessTokenTtlSeconds) {
}
