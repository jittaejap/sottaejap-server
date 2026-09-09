package kr.sottaejap.server.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.YearMonth;

/**
 * 04 §1 MonthlySnapshot — <b>확정된 지난달</b>의 리포트 (E-94). 스키마 정본은 V1 {@code monthly_snapshots}다
 * (ddl-auto=validate, {@code uq_monthly_snapshots(user_id, year_month)}).
 *
 * <p>지난달은 첫 조회 때 계산해 여기 저장하고 그 뒤로는 다시 계산하지 않는다 — 회고를 더해도 지난달 숫자가
 * 흔들리면 안 된다. 이번 달은 매번 계산하고 저장하지 않으므로 이 테이블에 현재 연월 행은 없다.
 *
 * <p>행은 {@link kr.sottaejap.server.report.repository.MonthlySnapshotRepository#insertIfAbsent}가 만든다.
 * 값이 바뀌는 경로가 없어 setter도 갱신 메서드도 없다.
 *
 * <p>{@code year_month}는 CHAR(7)이다. {@link YearMonth} ↔ 문자열 변환은 이 클래스에만 둔다.
 */
@Entity
@Table(name = "monthly_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** `2026-08`. DB는 CHAR(7)이라 JDBC 타입을 맞춰야 validate가 통과한다. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "year_month", nullable = false, length = 7)
    private String yearMonth;

    @Column(name = "total_spending")
    private Integer totalSpending;

    @Column(name = "unsatisfied_count")
    private Integer unsatisfiedCount;

    @Column(name = "repeat_count")
    private Integer repeatCount;

    /** 전월 값이 없으면 null (E-94 ①). */
    @Column(name = "saved_amount")
    private Integer savedAmount;

    /** DB 컬럼 형식 `YYYY-MM`. 조회 조건을 만들 때도 이것을 쓴다. */
    public static String text(YearMonth yearMonth) {
        return yearMonth.toString();
    }
}
