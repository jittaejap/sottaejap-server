package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.retrospect.service.ClusterRecomputeService;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드와 묶음 재계산을 엮는다 (E-95 · 06 R24). 업로드가 분석 기준월(E-60)을 바꾸는 순간이라, 재계산하지 않으면
 * 새 기준월 이름표에 지난달 금액이 붙는다 — 기준월은 실시간으로 계산하고(E-78) 금액 · 부담 · 좌표는 묶음 행에
 * 저장된 값이기 때문이다(E-61).
 *
 * <p><b>이 클래스에 {@code @Transactional}이 없다.</b> {@link TransactionService#upload}를 커밋한 뒤 재계산을
 * 별도 트랜잭션으로 부른다 — 업로드 응답 시간에 전체 재계산과 제안 재생성(E-81)을 넣지 않고, 재계산이 실패해도
 * 파싱에 성공한 CSV를 되돌리지 않는다. {@link kr.sottaejap.server.retrospect.service.RetrospectServiceImpl}이
 * 쓰기와 AI 명명을 가르는 방식과 같다 (E-64).
 *
 * <p>회고가 0건이면 재계산은 스스로 아무것도 하지 않고 끝난다 (E-96) — 그 판단은
 * {@code ClusterRecomputeServiceImpl}이 회고를 읽은 자리에서 한다. 여기서 건수를 미리 세지 않는다.
 *
 * <p>재계산을 {@link TransactionServiceImpl}에 직접 주입할 수는 없다. {@code ClusterRecomputeServiceImpl}이
 * 기준월을 얻으려고 이미 {@link TransactionService}를 물고 있어(E-78) 순환 참조가 되고,
 * {@code spring.main.allow-circular-references}가 기본값 {@code false}라 기동이 죽는다.
 */
@Component
@RequiredArgsConstructor
public class TransactionUploadFacade {

    private static final Logger log = LoggerFactory.getLogger(TransactionUploadFacade.class);

    private final TransactionService transactionService;
    private final ClusterRecomputeService clusterRecomputeService;

    public TransactionUploadResponse upload(long userId, MultipartFile file) {
        TransactionUploadResponse response = transactionService.upload(userId, file);
        // 새로 저장된 거래가 0건이어도 재계산한다 (E-96). 전량 중복 재업로드는 재계산이 실패해 어긋난 값이
        // 남았을 때 사용자가 가장 먼저 하는 행동이라, 여기서 건너뛰면 복구 경로가 막힌다.
        recompute(userId);
        return response;
    }

    /**
     * 재계산이 실패해도 업로드는 200으로 응답한다. 업로드는 사용자가 파일을 고르고 기다리는 행위라 성공률이 응답
     * 속도보다 앞서고, 어긋난 값은 다음 회고 저장이나 예산 변경의 재계산이 덮는다.
     *
     * <p>그 사이 사용자가 새 기준월 이름표에 지난달 금액을 볼 수 있다 — 이것이 이 선택의 비용이고 버그가 아니다.
     */
    private void recompute(long userId) {
        try {
            clusterRecomputeService.recomputeAll(userId);
        } catch (RuntimeException recomputeFailed) {
            log.warn("업로드 뒤 묶음 재계산에 실패했습니다 — 거래는 저장됐고 기준월 금액은 다음 재계산까지 옛 값입니다."
                    + " userId={}", userId, recomputeFailed);
        }
    }
}
