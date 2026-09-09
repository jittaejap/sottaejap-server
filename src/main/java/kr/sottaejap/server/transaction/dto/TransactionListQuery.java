package kr.sottaejap.server.transaction.dto;

import java.time.LocalDate;

/**
 * 05 §2 `GET /transactions`의 쿼리 (E-93). {@code from}·{@code to}는 KST 날짜이고 양끝을 포함한다.
 * {@code hasRetrospect}는 null이면 전체다. 기본값(page 0 · size 20)은 컨트롤러가 채우고, 상한과 400은 서비스가 정한다.
 */
public record TransactionListQuery(LocalDate from,
                                   LocalDate to,
                                   String category,
                                   Boolean hasRetrospect,
                                   int page,
                                   int size) {
}
