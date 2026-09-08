package kr.sottaejap.server.onboarding.dto;

/**
 * `POST /onboarding/complete` 응답 (05 §2 · FR-09-02).
 *
 * @param onboardingCompleted 항상 true다. 다음 로그인부터 `GET /users/me`가 같은 값을 준다
 * @param clusterCount        지도에 찍히는 점의 수 — 유효 묶음(E-72)의 개수다
 */
public record OnboardingCompleteResponse(boolean onboardingCompleted, int clusterCount) {
}
