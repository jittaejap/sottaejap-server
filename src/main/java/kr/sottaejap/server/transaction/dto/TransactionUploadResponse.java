package kr.sottaejap.server.transaction.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * POST /transactions/upload 응답 (05 §2).
 *
 * @param skippedRows 건너뛴 행. {@code row}는 파일의 물리적 줄 번호(1부터, 머리글 포함)라
 *                    사용자가 업로드한 파일을 열어 그 줄을 바로 찾을 수 있다.
 */
public record TransactionUploadResponse(int importedCount,
                                        int skippedCount,
                                        LocalDate periodFrom,
                                        LocalDate periodTo,
                                        List<SkippedRow> skippedRows) {

    public record SkippedRow(int row, String reason) {
    }
}
