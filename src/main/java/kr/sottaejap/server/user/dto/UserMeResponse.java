package kr.sottaejap.server.user.dto;

import kr.sottaejap.server.common.enums.AuthProvider;
import kr.sottaejap.server.user.domain.User;

/**
 * GET /users/me (05 §2). 클라이언트는 onboardingCompleted로 2-1 / 3-1 진입을 가른다 (FR-09-03).
 *
 * @param outlierBaseAmount 큰 금액 기준 금액, 원 단위 (E-115). 정하지 않았으면 null — 화면은 0으로 대체하지 않는다
 * @param analysisYearMonth 사용자의 최근 거래월 `2026-08` (E-60). 거래가 없으면 null
 */
public record UserMeResponse(
        Long id,
        String email,
        String nickname,
        AuthProvider authProvider,
        Integer monthlyBudget,
        Double outlierThreshold,
        Integer outlierBaseAmount,
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
                user.getOutlierBaseAmount(),
                user.getRetrospectDelayDays(),
                user.isOnboardingCompleted(),
                analysisYearMonth
        );
    }
}
