package kr.sottaejap.server.onboarding.service;

import kr.sottaejap.server.onboarding.dto.OnboardingCompleteResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingStartRequest;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;

/** 온보딩 3·4단계 (05 §2 #18 · #21 · FR-09-01,02). */
public interface OnboardingService {

    /**
     * 표본 회고 시작 — 기간 안의 미회고 거래를 균등 간격으로 골라 돌려준다 (E-92).
     * 고른 목록은 저장하지 않는다 (E-49 · E-65).
     */
    CandidateListResponse start(long userId, OnboardingStartRequest request);

    /** 온보딩 완료 — 플래그를 세우고 초기 만족도 지도를 만든다 (FR-09-02). 두 번 불러도 결과가 같다. */
    OnboardingCompleteResponse complete(long userId);
}
