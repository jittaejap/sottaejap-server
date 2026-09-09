package kr.sottaejap.server.transaction.controller;

import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.transaction.dto.TransactionListQuery;
import kr.sottaejap.server.transaction.dto.TransactionListResponse;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import kr.sottaejap.server.transaction.service.TransactionService;
import kr.sottaejap.server.transaction.service.TransactionUploadFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 쓰기(업로드)는 {@link TransactionUploadFacade}, 조회는 {@link TransactionService} 직접이다 (06 R26 · PR #31 합의).
 * 조회를 파사드에 통과시키면 아무 일도 하지 않는 위임 메서드가 생긴다.
 */
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionUploadFacade transactionUploadFacade;
    private final TransactionService transactionService;

    /**
     * 05 §2 GET /transactions — 기간 · 카테고리 · 회고 여부 · 페이징 (E-93 · FR-02-02). 회고 이력 탭은
     * {@code hasRetrospect=true}다. 타입이 맞지 않는 값(날짜 · 정수 · 불리언)은 바인딩 실패로 400 INVALID_INPUT이다.
     */
    @GetMapping
    public ApiResponse<TransactionListResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean hasRetrospect,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(transactionService.list(user.userId(),
                new TransactionListQuery(from, to, category, hasRetrospect, page, size)));
    }

    /**
     * 05 §2 POST /transactions/upload — CSV와 XLSX를 받는다. 그 밖의 확장자는 INVALID_FILE_FORMAT이다.
     *
     * <p>업로드가 기준월을 바꾸므로 묶음 재계산까지 엮어야 한다. 그 배치 자리가
     * {@link TransactionUploadFacade}다 (E-95) — 컨트롤러는 계속 위임만 한다.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TransactionUploadResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam MultipartFile file) {
        return ApiResponse.success(transactionUploadFacade.upload(user.userId(), file));
    }
}
