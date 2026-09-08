package kr.sottaejap.server.transaction.controller;

import kr.sottaejap.server.auth.security.AuthenticatedUser;
import kr.sottaejap.server.common.response.ApiResponse;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import kr.sottaejap.server.transaction.service.TransactionService;
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

    private final TransactionService transactionService;

    /** 05 §2 POST /transactions/upload — 지금은 CSV만 받는다. XLSX는 INVALID_FILE_FORMAT이다. */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TransactionUploadResponse> upload(@AuthenticationPrincipal AuthenticatedUser user,
                                                         @RequestParam MultipartFile file) {
        return ApiResponse.success(transactionService.upload(user.userId(), file));
    }
}
