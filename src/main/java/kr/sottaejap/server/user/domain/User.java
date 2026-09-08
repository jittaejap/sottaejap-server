package kr.sottaejap.server.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.sottaejap.server.common.enums.AuthProvider;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 04 §1 User. 스키마 정본은 db/migration/V1__init.sql이다 (ddl-auto=validate).
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 카카오 이메일은 선택 동의라 비어 있을 수 있다 (E-56 · V4). 이메일로 계정을 합치지 않는다. */
    @Column
    private String email;

    /** 마이페이지 프로필 표시 이름 (E-56 · V4). null이면 클라이언트가 "사용자"로 대체한다. */
    @Column(length = 100)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider;

    @Column(name = "provider_user_id")
    private String providerUserId;

    /** 지출 부담 분모 (C-1). */
    @Column(name = "monthly_budget")
    private Integer monthlyBudget;

    @Column(name = "outlier_threshold")
    private Double outlierThreshold;

    /** 전체 평균 — 축소 추정용 캐시 (B-3). */
    @Column(name = "avg_satisfaction")
    private Double avgSatisfaction;

    @Column(name = "retrospect_delay_days", nullable = false)
    private int retrospectDelayDays;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    /** 소셜 첫 로그인 (E-56). 별도 회원가입 API 없이 여기서 사용자가 생긴다. D+1 · 온보딩 미완료로 시작한다. */
    public static User social(AuthProvider authProvider, String providerUserId, String nickname, String email) {
        User user = new User();
        user.authProvider = authProvider;
        user.providerUserId = providerUserId;
        user.nickname = nickname;
        user.email = email;
        user.retrospectDelayDays = 1;
        user.onboardingCompleted = false;
        return user;
    }

    /** 회고 저장 시 규칙 엔진이 낸 사용자 전체 평균으로 갱신한다 (E-61). */
    public void updateAvgSatisfaction(Double avgSatisfaction) {
        this.avgSatisfaction = avgSatisfaction;
    }

    /**
     * 온보딩 2단계 · 마이페이지의 설정 변경 (FR-01-03,04,06). null은 "그대로 두기"다 —
     * 예산은 필수 값이라(03 W-9) 한 번 정한 뒤 비우는 경로를 두지 않는다.
     */
    public void updateSettings(Integer monthlyBudget, Double outlierThreshold, Integer retrospectDelayDays) {
        if (monthlyBudget != null) {
            this.monthlyBudget = monthlyBudget;
        }
        if (outlierThreshold != null) {
            this.outlierThreshold = outlierThreshold;
        }
        if (retrospectDelayDays != null) {
            this.retrospectDelayDays = retrospectDelayDays;
        }
    }
}
