package kr.sottaejap.server.retrospect.service;

import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.Satisfaction;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.retrospect.repository.RetrospectWithTransaction;
import kr.sottaejap.server.rules.RuleParams;
import kr.sottaejap.server.rules.candidate.CandidateInput;
import kr.sottaejap.server.rules.candidate.CandidateRule;
import kr.sottaejap.server.rules.cluster.ClusterKeyRule;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 회고 후보 ⓪ (E-62). "오늘"은 Clock으로 보고, 판정은 {@link CandidateRule}에 맡긴다 (E-18).
 *
 * <p>날짜 조건 ①(D+1)과 ②(이미 회고한 거래 제외)만 쿼리로 거른다. ③④⑤는 규칙 엔진이 정하므로
 * 서비스는 금액 목록·배수·LOW 회고 수를 모아 넘기기만 한다.
 */
@Service
@RequiredArgsConstructor
public class CandidateServiceImpl implements CandidateService {

    private final TransactionRepository transactionRepository;
    private final RetrospectRepository retrospectRepository;
    private final UserRepository userRepository;
    private final RuleParams params;
    private final Clock clock;

    /**
     * {@inheritDoc}
     *
     * <p>쿼리에는 {@code limit}이 아니라 {@link CandidateService#MAX_LIMIT}을 건다. 쿼리는 날짜 조건만 알고 규칙 ③④⑤는
     * 모르기 때문에, {@code limit}건만 읽으면 그 안이 전부 미매칭 거래일 때 결과가 빈 목록이 된다.
     * 넉넉히 읽고 규칙을 적용한 뒤 {@code limit}으로 자른다. 대신 최신 {@code MAX_LIMIT}건 안에
     * 매칭이 하나도 없으면 후보가 없다고 본다 — 사용자가 더 오래된 거래를 보려면 {@code from}·{@code to}로
     * 범위를 지정한다 (E-48).
     */
    @Override
    @Transactional(readOnly = true)
    public List<CandidateView> findCandidates(long userId, int limit, LocalDate from, LocalDate to) {
        Baseline baseline = loadBaseline(userId);
        // limit 1 미만은 RetrospectServiceImpl.candidates가 이미 400으로 걸렀다. 상한만 조용히 자른다 (05 §2).
        int size = Math.min(limit, CandidateService.MAX_LIMIT);

        List<Transaction> transactions = transactionRepository.findCandidates(
                userId, startOf(from), toExclusive(baseline.user(), to), PageRequest.of(0, CandidateService.MAX_LIMIT));

        List<CandidateView> candidates = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (candidates.size() >= size) {
                break;
            }
            Optional<ReasonCode> reasonCode = CandidateRule.evaluate(inputFor(baseline, transaction), params);
            reasonCode.ifPresent(code ->
                    candidates.add(CandidateView.from(transaction, code, ReasonTemplate.reasonFor(code))));
        }
        return candidates;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ReasonCode reasonCodeFor(long userId, Transaction transaction) {
        Baseline baseline = loadBaseline(userId);
        return CandidateRule.evaluate(inputFor(baseline, transaction), params).orElse(ReasonCode.MANUAL_PICK);
    }

    /** 규칙 입력의 재료를 한 번에 읽는다 (E-62 ③④⑤). 거래마다 다시 조회하면 후보 100건이 200질의가 된다. */
    private Baseline loadBaseline(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        RuleParams.Candidate candidate = RuleParams.require(params.candidate(), "rules.candidate");
        int baselineDays = RuleParams.require(candidate.outlierBaselineDays(), "rules.candidate.outlier-baseline-days");
        OffsetDateTime since = LocalDate.now(clock).minusDays(baselineDays)
                .atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();

        Map<BaselineKey, List<Transaction>> bySlot = new HashMap<>();
        Map<String, List<Transaction>> byCategory = new HashMap<>();
        for (Transaction transaction : transactionRepository
                .findAllByUserIdAndOccurredAtGreaterThanEqual(userId, since)) {
            String category = categoryOf(transaction);
            bySlot.computeIfAbsent(new BaselineKey(category, transaction.getTimeSlot()), key -> new ArrayList<>())
                    .add(transaction);
            byCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(transaction);
        }
        return new Baseline(user, bySlot, byCategory, lowSatisfactionCounts(userId));
    }

    /** ⑤ 상위 키(`카테고리|시간대`)별 LOW 회고 수 (E-59 · E-62). */
    private Map<String, Integer> lowSatisfactionCounts(long userId) {
        Map<String, Integer> counts = new HashMap<>();
        for (RetrospectWithTransaction row : retrospectRepository.findAllWithTransactionByUserId(userId)) {
            if (row.retrospect().getSatisfaction() != Satisfaction.LOW) {
                continue;
            }
            counts.merge(parentKeyOf(row.transaction()), 1, Integer::sum);
        }
        return counts;
    }

    /** 거래 한 건의 규칙 입력. `findCandidates`와 `reasonCodeFor`가 같은 조립을 쓴다 (E-63). */
    private CandidateInput inputFor(Baseline baseline, Transaction transaction) {
        String category = categoryOf(transaction);
        return new CandidateInput(
                transaction.getAmount(),
                amountsExcept(baseline.bySlot().get(new BaselineKey(category, transaction.getTimeSlot())),
                        transaction.getId()),
                amountsExcept(baseline.byCategory().get(category), transaction.getId()),
                multiplier(baseline.user()),
                baseline.user().getMonthlyBudget(),
                baseline.lowCounts().getOrDefault(parentKeyOf(transaction), 0));
    }

    /** 기준선에서 이 거래 자신을 뺀다 — 자기 금액이 중앙값을 끌어올리면 이상치 판정이 무뎌진다. */
    private static List<Integer> amountsExcept(List<Transaction> group, Long id) {
        if (group == null) {
            return List.of();
        }
        return group.stream()
                .filter(transaction -> !Objects.equals(transaction.getId(), id))
                .map(Transaction::getAmount)
                .toList();
    }

    /** ④ 배수는 사용자 설정이 우선이고, 없으면 E-46 프리셋 standard다 (E-62). */
    private Double multiplier(User user) {
        Double outlierThreshold = user.getOutlierThreshold();
        if (outlierThreshold != null) {
            return outlierThreshold;
        }
        // 값만 빈 RULES_SENSITIVITY_STANDARD=는 null을 바인딩한다. 여기서 막지 않으면 CandidateRule이
        // 예외 없이 false를 돌려 TIMESLOT_OUTLIER 후보가 통째로 사라진다.
        return RuleParams.require(
                RuleParams.require(params.sensitivity(), "rules.sensitivity").standard(),
                "rules.sensitivity.standard");
    }

    /**
     * ① D+1 — KST 날짜가 `오늘 − retrospectDelayDays` 이하인 거래만 본다 (E-62).
     * `to`를 지정하면 더 이른 쪽을 쓴다. 반환값은 열린 끝(exclusive)이다.
     */
    private OffsetDateTime toExclusive(User user, LocalDate to) {
        OffsetDateTime cutoff = LocalDate.now(clock)
                .minusDays(user.getRetrospectDelayDays())
                .plusDays(1)
                .atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
        if (to == null) {
            return cutoff;
        }
        OffsetDateTime requested = to.plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
        return requested.isBefore(cutoff) ? requested : cutoff;
    }

    /** `from`이 없으면 상한을 두지 않는다 — 오래된 거래에도 회고 자격이 있다 (E-48). */
    private static OffsetDateTime startOf(LocalDate from) {
        return from == null ? null : from.atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }

    private static String categoryOf(Transaction transaction) {
        return transaction.getCategory() == null ? ClusterKeyRule.UNCATEGORIZED : transaction.getCategory();
    }

    private String parentKeyOf(Transaction transaction) {
        return ClusterKeyRule.parentKey(transaction.getCategory(), transaction.getTimeSlot(), params);
    }

    /** 이상치 기준선 그룹 키. 카테고리는 미분류(`기타`)로 채운 값을 쓴다. */
    private record BaselineKey(String category, TimeSlot timeSlot) {
    }

    /** 한 사용자의 규칙 입력 재료 — 거래마다 다시 읽지 않으려고 모아 둔다. */
    private record Baseline(
            User user,
            Map<BaselineKey, List<Transaction>> bySlot,
            Map<String, List<Transaction>> byCategory,
            Map<String, Integer> lowCounts
    ) {
    }
}
