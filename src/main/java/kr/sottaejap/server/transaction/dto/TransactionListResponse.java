package kr.sottaejap.server.transaction.dto;

import java.util.List;

/**
 * 05 §2 `GET /transactions` 응답 (E-93). 봉투의 {@code data}는 object여야 하므로 목록을 배열로 바로 돌려주지 않는다.
 * {@code size}는 상한(100)을 적용한 실제 페이지 크기이고 {@code totalElements}는 필터를 적용한 총건수다.
 */
public record TransactionListResponse(List<TransactionView> transactions,
                                      int page,
                                      int size,
                                      long totalElements,
                                      int totalPages) {
}
