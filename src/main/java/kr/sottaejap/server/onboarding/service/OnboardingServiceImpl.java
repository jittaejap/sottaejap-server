package kr.sottaejap.server.onboarding.service;

import kr.sottaejap.server.common.enums.ReasonCode;
import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.onboarding.dto.OnboardingCompleteResponse;
import kr.sottaejap.server.onboarding.dto.OnboardingStartRequest;
import kr.sottaejap.server.retrospect.dto.CandidateListResponse;
import kr.sottaejap.server.retrospect.dto.CandidateView;
import kr.sottaejap.server.retrospect.repository.BehaviorClusterRepository;
import kr.sottaejap.server.retrospect.service.CandidateService;
import kr.sottaejap.server.retrospect.service.ClusterRecomputeService;
import kr.sottaejap.server.retrospect.service.ReasonTemplate;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import kr.sottaejap.server.user.domain.User;
import kr.sottaejap.server.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 온보딩 3·4단계 (E-92).
 *
 * <p>표본은 후보 API(#8)로 갈음할 수 없다. 후보 규칙 ③④⑤ 중 ⑤는 이전 회고가 있어야 걸리는데 온보딩 시점의
 * 회고는 0건이고, 규칙은 최신 100건 안에서만 본다 (E-62). 두 달치 CSV를 올려도 20건이 모이지 않는다.
 * {@code ONBOARDING_SAMPLE}을 만드는 경로는 여기 하나뿐이다.
 */
@Service
@RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final ClusterRecomputeService clusterRecomputeService;
    private final BehaviorClusterRepository behaviorClusterRepository;

    /**
     * 표본을 고르기 전에 읽는 거래 수의 안전 상한.
     *
     * <p>ponytail: 기간에 이보다 많은 거래가 있으면 최신 {@code SCAN_LIMIT}건 안에서만 고른다. 온보딩 CSV는
     * 개인의 두 달치라 여기에 닿지 않지만, 업로드를 여러 번 하거나 클라이언트가 기간을 넓게 실어도 메모리가
     * 요청을 따라 늘지 않게 막는다. 수년치를 한 번에 다루게 되면 이 자리에서 페이지를 나눈다.
     */
    private static final int SCAN_LIMIT = 2_000;

    /**
     * {@inheritDoc}
     *
     * <p>상한은 후보 API와 같은 {@link CandidateService#MAX_LIMIT}이고, 넘겨도 400이 아니라 조용히 자른다 (05 §2).
     * 기간을 {@link #SCAN_LIMIT}까지 한 번에 읽는다 — 기간에 표본을 펼치려면 전체가 몇 건인지 먼저 알아야 한다.
     */
    @Override
    @Transactional(readOnly = true)
    public CandidateListResponse start(long userId, OnboardingStartRequest request) {
        if (request.periodFrom().isAfter(request.periodTo())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        List<Transaction> inPeriod = transactionRepository.findCandidates(
                userId,
                startOf(request.periodFrom()),
                startOf(request.periodTo().plusDays(1)),
                PageRequest.of(0, SCAN_LIMIT));
        return new CandidateListResponse(sample(inPeriod, Math.min(request.sampleSize(), CandidateService.MAX_LIMIT)));
    }

    /**
     * 기간에 표본을 펼친다 (E-92). 최신순 목록의 앞에서 그냥 자르면 기간의 마지막 한 주만 남아
     * 첫 만족도 지도의 묶음이 한 주에 쏠린다. 난수를 쓰지 않으므로 같은 요청이면 같은 표본이다 (E-18).
     *
     * <p>고를 자리를 {@code i × 전체 ÷ 표본수}로 되짚는다. 간격을 정수로 먼저 구하면 전체가 표본수의 배수가
     * 아닐 때 나머지가 통째로 남아 가장 오래된 구간을 아예 보지 않는다 — 59건에서 20건을 고를 때 간격이 2가
     * 되어 39번째에서 멈추고 뒤 20건(34%)이 빠졌다. 전체가 표본수의 두 배 미만이면 간격이 1이 되어, 막으려던
     * "앞에서 그냥 자르기"와 같아지기까지 했다.
     */
    private static List<CandidateView> sample(List<Transaction> transactions, int size) {
        // 거래가 표본보다 적으면 있는 만큼만 준다. 0건이거나 size가 1보다 작으면 나눗셈까지 가지 않는다.
        int picks = Math.min(size, transactions.size());
        List<CandidateView> sample = new ArrayList<>();
        for (int i = 0; i < picks; i++) {
            Transaction picked = transactions.get((int) ((long) i * transactions.size() / picks));
            sample.add(CandidateView.from(picked, ReasonCode.ONBOARDING_SAMPLE,
                    ReasonTemplate.reasonFor(ReasonCode.ONBOARDING_SAMPLE)));
        }
        return sample;
    }

    /**
     * {@inheritDoc}
     *
     * <p>회고가 0건이어도 플래그는 저장한다 — 온보딩을 끝냈는데 홈에 못 들어가는 상태를 만들지 않는다.
     * 묶음 이름은 여기서 짓지 않는다. {@code POST /retrospects}가 저장할 때마다 이름 없는 묶음을 이미 채운다 (E-64).
     */
    @Override
    @Transactional
    public OnboardingCompleteResponse complete(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
        user.completeOnboarding();
        clusterRecomputeService.recomputeAll(userId);
        // 지도가 세는 것과 같은 조회를 쓴다 (E-72). 재계산 결과를 직접 세면 롤업된 리프까지 세어 점보다 큰 수가 나온다.
        return new OnboardingCompleteResponse(true, behaviorClusterRepository.findEffectiveByUserId(userId).size());
    }

    /** KST 자정 기준. TIMESTAMPTZ는 UTC로 읽히므로 경계는 항상 +09:00으로 만든다. */
    private static OffsetDateTime startOf(LocalDate date) {
        return date.atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }
}
