package kr.sottaejap.server.user.dto;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.user.domain.User;

/**
 * GET /users/me (05 §2). 클라이언트는 onboardingCompleted로 2-1 / 3-1 진입을 가른다 (FR-09-03).
 *
 * @param analysisYearMonth 04 User 엔티티에 없는 필드. 산출 규칙이 문서에 없어 지금은 null — 05·04 갭 (AGENTS.md 참고)
 */
public record UserMeResponse(
        Long id,
        String email,
        AuthProvider authProvider,
        Integer monthlyBudget,
        Double outlierThreshold,
        int retrospectDelayDays,
        boolean onboardingCompleted,
        String analysisYearMonth
) {

    public static UserMeResponse from(User user) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getAuthProvider(),
                user.getMonthlyBudget(),
                user.getOutlierThreshold(),
                user.getRetrospectDelayDays(),
                user.isOnboardingCompleted(),
                null
        );
    }
}
