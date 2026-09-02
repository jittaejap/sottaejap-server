package kr.sottaejap.server.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * CORS 명시적 allowlist. wildcard 금지.
 */
@Component
public class AllowedOriginPolicy {

    private final List<String> allowedOrigins;

    public AllowedOriginPolicy(@Value("${auth.allowed-origins}") String configuredOrigins) {
        LinkedHashSet<String> origins = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        if (origins.isEmpty()) {
            throw new IllegalArgumentException("auth.allowed-origins requires at least one origin");
        }
        if (origins.contains("*")) {
            throw new IllegalArgumentException("Wildcard origin is not allowed");
        }
        this.allowedOrigins = List.copyOf(origins);
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }
}
