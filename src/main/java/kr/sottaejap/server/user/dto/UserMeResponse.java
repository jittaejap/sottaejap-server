package kr.sottaejap.server.user.dto;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.user.domain.User;

/**
 * GET /users/me (05 §2). 클라이언트는 onboardingCompleted로 2-1 / 3-1 진입을 가른다 (FR-09-03).
 *
 * @param analysisYearMonth 사용자의 최근 거래월 `2026-08` (E-60). 거래가 없으면 null
 */
public record UserMeResponse(
        Long id,
        String email,
        String nickname,
        AuthProvider authProvider,
        Integer monthlyBudget,
        Double outlierThreshold,
        int retrospectDelayDays,
        boolean onboardingCompleted,
        String analysisYearMonth
) {

    public static UserMeResponse from(User user, String analysisYearMonth) {
        return new UserMeResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getAuthProvider(),
                user.getMonthlyBudget(),
                user.getOutlierThreshold(),
                user.getRetrospectDelayDays(),
                user.isOnboardingCompleted(),
                analysisYearMonth
        );
    }
}
