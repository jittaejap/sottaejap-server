package kr.sottaejap.server.transaction.controller;

import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import kr.sottaejap.server.transaction.service.TransactionUploadFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionUploadFacade transactionUploadFacade;

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
