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

    @Column(nullable = false)
    private String email;

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
}
