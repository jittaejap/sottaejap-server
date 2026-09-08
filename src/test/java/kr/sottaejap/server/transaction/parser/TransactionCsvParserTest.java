package kr.sottaejap.server.transaction.parser;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse.SkippedRow;
import kr.sottaejap.server.transaction.parser.TransactionCsvParser.ParseResult;
import kr.sottaejap.server.transaction.parser.TransactionCsvParser.ParsedRow;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 공통 3컬럼(거래일시 · 가맹점 · 금액) 파싱과 04 §4 공통 처리 규칙을 확인한다.
 *
 * <p>서식 3종 픽스처는 실제로 받은 파일의 머리글과 꼬리를 <b>글자 그대로</b> 옮기고 가맹점명 ·
 * 카드번호 · 계좌번호만 익명화한 것이다 (05 §5). 추측한 머리글로는 이 파서가 무엇을 못 읽었는지 드러나지 않는다.
 */
class TransactionCsvParserTest {

    private final TransactionCsvParser parser = new TransactionCsvParser();

    @Test
    void 카드사마다_다른_헤더를_키워드로_찾는다() {
        ParseResult kb = parser.parse(utf8("""
                이용일시,이용하신곳,이용금액,업종
                2026.08.25 20:22:30,○○마트,"12,000",유통
                """));
        ParseResult shinhan = parser.parse(utf8("""
                거래일자,가맹점명,거래금액
                2026-08-25 20:22,○○마트,12000
                """));

        assertEquals(1, kb.rows().size());
        assertEquals(1, shinhan.rows().size());
        assertEquals(OffsetDateTime.parse("2026-08-25T20:22:30+09:00"), kb.rows().get(0).occurredAt());
        assertEquals(OffsetDateTime.parse("2026-08-25T20:22:00+09:00"), shinhan.rows().get(0).occurredAt());
        assertEquals(12000, kb.rows().get(0).amount());
        assertEquals("유통", kb.rows().get(0).sourceCategory());
        assertNull(shinhan.rows().get(0).sourceCategory());
    }

    /** 실제 카드 내역서 머리글 13종을 그대로 옮긴 것. 추측이 아니라 실물이다. */
    private static final String REAL_CARD_HEADER =
            "거래일,카드구분,이용카드,가맹점명,업종,승인번호,금액,매입구분,이용구분,거래통화,최초결제일자,해외이용금액,취소상태";

    @Test
    void 실제_카드내역_머리글을_읽는다() {
        ParseResult result = parser.parse(utf8(REAL_CARD_HEADER + """
                
                2026.05.30 17:17,체크,본인000*,○○빵집,,37373238,17050.0,결제확정,일시불,,,,
                2026.04.10 21:49,체크,본인000*,○○쇼핑,,29414627,-10320.0,거래취소,일시불,,,,취소
                ,,,총 177건,,,2004068.0,,,,,,
                """));

        // 금액은 6번 컬럼이다. 비어 있는 해외이용금액(11번)을 잡으면 전부 금액 형식 오류가 된다.
        assertEquals(1, result.rows().size());
        assertEquals("○○빵집", result.rows().get(0).merchant());
        assertEquals(17050, result.rows().get(0).amount());
        assertEquals(OffsetDateTime.parse("2026-05-30T17:17+09:00"), result.rows().get(0).occurredAt());

        // 꼬리의 합계 행은 거래가 아니라 건너뛴 목록에도 없다. 취소 한 건만 남는다.
        assertEquals(List.of("취소·환불 거래"), result.skipped().stream().map(SkippedRow::reason).toList());
    }

    /**
     * 실제 카드 이용내역서. 두 가지가 한꺼번에 걸린다 —
     * 머리글이 따옴표 안 줄바꿈으로 물리적 네 줄에 걸쳐 있고, 날짜와 시각이 다른 칸에 있다.
     */
    @Test
    void 머리글이_여러_줄이고_날짜와_시각이_나뉘어_있어도_읽는다() {
        ParseResult result = parser.parse(utf8("""
                ,,,,,,,,,,,,,
                카드이용내역 조회,,,,,,,,,,,,,
                [조회기간: 2026.06.04 ~ 2026.09.03],,,,,,,,,,,,,
                이용일,이용시간,이용카드,승인번호,가맹점명,승인금액,"포인트
                사용",이용구분,"할부
                기간",매입,매입금액,"매입할인
                금액",매입취소금액,상태
                2026.09.03,18:15:08,본인0000,03560539,○○분식,"15,500 ",0 ,일시불,-,미매입,0 ,0 ,0 ,정상
                2026.08.30,09:42:49,본인0000,42007476,○○구독,"4,990 ",0 ,일시불,-,매입,"4,990 ",0 ,0 ,정상
                정상승인건수,,,110 ,,,,,,,,,,
                이하 여백 (End of document),,,,,,,,,,,,,
                """));

        assertEquals(2, result.rows().size());
        assertEquals("○○분식", result.rows().get(0).merchant());
        assertEquals(15500, result.rows().get(0).amount());
        // 이용일 + 이용시간을 붙여야 나오는 시각이다. 붙이지 않으면 "시간 정보 없음"으로 전부 버려진다.
        assertEquals(OffsetDateTime.parse("2026-09-03T18:15:08+09:00"), result.rows().get(0).occurredAt());
        assertEquals(TimeSlot.EVENING, TimeSlot.from(result.rows().get(0).occurredAt()));

        // 머리글이 4줄이므로 첫 거래는 8번째 줄이다. 사용자가 CSV를 열어 그 줄을 찾는다.
        assertEquals(8, result.rows().get(0).line());
        // 머리글 잔해도, 꼬리의 정상승인건수·이하 여백도 건너뛴 목록에 없다.
        assertEquals(List.of(), result.skipped());
    }

    /** 실제 통장 내역(KB 나라사랑우대통장)의 머리글. 조회기간·계좌번호 머리 4줄이 앞에 붙는다. */
    private static final String REAL_BANK_HEADER = "거래일시,적요,보낸분/받는분,송금메모,출금액,입금액,거래점,구분";

    @Test
    void 통장내역은_적요가_아니라_보낸분받는분을_가맹점으로_읽는다() {
        ParseResult result = parser.parse(utf8("""
                조회기간,2026.03.04 ~ 2026.09.03,,,,,,
                계좌번호,000000-00-000000,,,총잔액,,,
                예금종류,○○우대통장,,,출금가능금액,,,
                ,,,,,,,
                """ + REAL_BANK_HEADER + """
                
                2026.09.02 14:24:56,체크카드,○○커피,,"10,000",0,KB카드,
                ,,,합계,"6,569,378","7,940,381",,
                """));

        // 적요("체크카드")를 잡으면 모든 행의 가맹점이 같아져 회고가 성립하지 않는다.
        assertEquals(1, result.rows().size());
        assertEquals("○○커피", result.rows().get(0).merchant());
        assertEquals(10000, result.rows().get(0).amount());
        assertEquals(List.of(), result.skipped());
    }

    @Test
    void 통장에서_카드_결제가_아닌_거래는_건너뛴다() {
        ParseResult result = parser.parse(utf8(REAL_BANK_HEADER + """
                
                2026.09.02 14:24:56,체크카드,○○커피,,"10,000",0,KB카드,
                2026.09.02 11:01:15,오픈뱅킹출금,토스 홍길동,,"14,000",0,스타뱅,
                2026.08.29 13:02:17,FBS 출금,○○페이충전,,"10,000",0,ERP사,
                2026.08.27 03:49:37,현금IC,○○은행ATM,,"18,300",0,KB카드,
                2026.08.28 11:16:38,전자금융,고용서울동부,,0,"285,000",하나은행,
                2026.08.20 10:00:00,체크카드,○○마트,,0,"1,200",KB카드,
                """));

        // 송금·충전·현금인출은 가맹점 소비가 아니다. 걸러내지 않으면 "토스 홍길동"이 가맹점이 된다.
        assertEquals(1, result.rows().size());
        assertEquals("○○커피", result.rows().get(0).merchant());

        // 카드 결제인데 출금이 0인 행만 입금 행으로 따로 알린다 — 사유를 뭉뚱그리지 않는다.
        assertEquals(List.of("카드 결제 아님", "카드 결제 아님", "카드 결제 아님", "카드 결제 아님", "출금 없음 — 입금 행"),
                result.skipped().stream().map(SkippedRow::reason).toList());
        assertEquals(List.of(3, 4, 5, 6, 7), result.skipped().stream().map(SkippedRow::row).toList());
    }

    @Test
    void 머리글_앞의_제목줄을_건너뛴다() {
        ParseResult result = parser.parse(utf8("""
                KB국민카드 이용대금 조회
                조회기간 : 2026.03.03 ~ 2026.09.03

                이용일시,이용하신곳,이용금액
                2026.08.25 20:22,○○마트,12000
                """));

        assertEquals(1, result.rows().size());
        assertEquals(5, result.rows().get(0).line());
    }

    @Test
    void 따옴표_안의_쉼표와_줄바꿈은_가맹점명으로_남는다() {
        ParseResult result = parser.parse(utf8("""
                거래일시,가맹점명,금액
                2026-08-25 20:22,"커피,빵집",4500
                2026-08-26 20:22,"윗줄
                아랫줄",4500
                """));

        assertEquals("커피,빵집", result.rows().get(0).merchant());
        assertEquals(4500, result.rows().get(0).amount());
        assertEquals("윗줄\n아랫줄", result.rows().get(1).merchant());
        assertEquals(3, result.rows().get(1).line());
    }

    @Test
    void 취소_환불과_시간_없는_행은_사유와_함께_건너뛴다() {
        ParseResult result = parser.parse(utf8("""
                거래일시,가맹점명,금액
                2026-08-25 20:22,○○마트,-12000
                2026-08-26,○○마트,12000
                어제,○○마트,12000
                2026-08-27 09:10,,12000
                2026-08-28 09:10,○○마트,
                """));

        assertEquals(0, result.rows().size());
        assertEquals(List.of("취소·환불 거래", "시간 정보 없음", "날짜 형식 오류", "가맹점명 없음", "금액 형식 오류"),
                result.skipped().stream().map(SkippedRow::reason).toList());
        assertEquals(List.of(2, 3, 4, 5, 6), result.skipped().stream().map(SkippedRow::row).toList());
    }

    @Test
    void EUC_KR로_저장된_파일도_읽는다() {
        byte[] eucKr = """
                거래일시,가맹점명,금액
                2026-08-25 20:22,○○마트,12000
                """.getBytes(Charset.forName("MS949"));

        ParseResult result = parser.parse(eucKr);

        assertEquals("○○마트", result.rows().get(0).merchant());
    }

    @Test
    void BOM으로_시작하는_UTF_8_파일도_읽는다() {
        byte[] withBom = ("﻿" + """
                거래일시,가맹점명,금액
                2026-08-25 20:22,○○마트,12000
                """).getBytes(StandardCharsets.UTF_8);

        ParseResult result = parser.parse(withBom);

        assertEquals(1, result.rows().size());
        assertEquals("○○마트", result.rows().get(0).merchant());
    }

    @Test
    void CRLF로_저장된_파일도_줄_번호가_어긋나지_않는다() {
        ParseResult result = parser.parse(utf8("거래일시,가맹점명,금액\r\n2026-08-25 20:22,○○마트,12000\r\n"));

        assertEquals(1, result.rows().size());
        assertEquals(2, result.rows().get(0).line());
    }

    @Test
    void 머리글을_못_찾으면_예외로_알린다() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse(utf8("아무 내용,없음\n1,2\n")));
    }

    @Test
    void 시간대는_네_구간으로_나뉘고_경계는_시작_시각을_포함한다() {
        assertEquals(TimeSlot.NIGHT, slotAt("2026-08-25 04:59"));
        assertEquals(TimeSlot.MORNING, slotAt("2026-08-25 05:00"));
        assertEquals(TimeSlot.DAY, slotAt("2026-08-25 11:00"));
        assertEquals(TimeSlot.EVENING, slotAt("2026-08-25 17:00"));
        assertEquals(TimeSlot.NIGHT, slotAt("2026-08-25 22:00"));
    }

    @Test
    void 같은_거래는_같은_시각으로_읽혀_중복_판별에_쓰인다() {
        ParsedRow first = parser.parse(utf8("거래일시,가맹점명,금액\n2026.08.25 20:22:00,○○마트,12000\n")).rows().get(0);
        ParsedRow second = parser.parse(utf8("이용일시,이용하신곳,이용금액\n2026-08-25 20:22,○○마트,\"12,000\"\n")).rows().get(0);

        assertEquals(first.occurredAt().toInstant(), second.occurredAt().toInstant());
        assertTrue(first.occurredAt().toString().endsWith("+09:00"));
    }

    private TimeSlot slotAt(String dateTime) {
        OffsetDateTime occurredAt =
                parser.parse(utf8("거래일시,가맹점명,금액\n" + dateTime + ",○○마트,12000\n")).rows().get(0).occurredAt();
        return TimeSlot.from(occurredAt);
    }

    private static byte[] utf8(String csv) {
        return csv.getBytes(StandardCharsets.UTF_8);
    }
}
