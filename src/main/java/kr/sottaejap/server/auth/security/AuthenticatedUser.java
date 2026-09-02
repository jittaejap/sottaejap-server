package kr.sottaejap.server.auth.security;

import java.security.Principal;

/**
 * SecurityContext에 올라가는 principal. 컨트롤러는 {@code @AuthenticationPrincipal AuthenticatedUser}로 받는다.
 */
public record AuthenticatedUser(long userId) implements Principal {

    public AuthenticatedUser {
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be positive");
        }
    }

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
