package kr.sottaejap.server.retrospect.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kr.sottaejap.server.common.enums.RetrospectSource;
import kr.sottaejap.server.common.enums.RetrospectStatus;
import kr.sottaejap.server.common.enums.Satisfaction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 04 §1 Retrospect. 거래당 하나(`transaction_id` UNIQUE — 중복은 409). 스키마 정본은 V1__init.sql (ddl-auto=validate).
 *
 * <p>purpose·companion은 사용자가 확인한 표준 태그 또는 null이다 (E-20). 자유 문자열은 서비스가 400으로 거른다.
 * reasonCode는 두지 않는다 — 후보 선별은 조회 시점 계산이라 재현된다 (04 §0 v1.9).
 */
@Entity
@Table(name = "retrospects")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Retrospect {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Satisfaction satisfaction;

    @Column(length = 20)
    private String purpose;

    @Column(name = "purpose_raw", columnDefinition = "text")
    private String purposeRaw;

    @Column(length = 20)
    private String companion;

    @Column(name = "companion_raw", columnDefinition = "text")
    private String companionRaw;

    /** true / false / null(미확정) (E-24). */
    @Column(name = "repeat_intent")
    private Boolean repeatIntent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RetrospectStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RetrospectSource source;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** 필수 항목이 모인 저장이므로 COMPLETED로 만든다 (FR-04-14). PAUSED 저장은 P2. */
    public static Retrospect completed(Long transactionId, Satisfaction satisfaction, String purpose, String companion,
                                       Boolean repeatIntent, RetrospectSource source, OffsetDateTime createdAt) {
        Retrospect retrospect = new Retrospect();
        retrospect.transactionId = transactionId;
        retrospect.satisfaction = satisfaction;
        retrospect.purpose = purpose;
        retrospect.companion = companion;
        retrospect.repeatIntent = repeatIntent;
        retrospect.status = RetrospectStatus.COMPLETED;
        retrospect.source = source;
        retrospect.createdAt = createdAt;
        return retrospect;
    }
}
