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
 * 3사 공통 3컬럼(거래일시 · 가맹점 · 금액) 파싱과 04 §4 공통 처리 규칙을 확인한다.
 * 실제 카드사 헤더 문자열은 아직 미확보라(06 #11) 자주 쓰이는 표기로 대신한다.
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

    /** 실제 카드 내역(오진호-3to5.xlsx)의 머리글 13종을 그대로 옮긴 것. 추측이 아니라 실물이다. */
    private static final String REAL_HEADER =
            "거래일,카드구분,이용카드,가맹점명,업종,승인번호,금액,매입구분,이용구분,거래통화,최초결제일자,해외이용금액,취소상태";

    @Test
    void 실제_카드내역_머리글을_읽는다() {
        ParseResult result = parser.parse(utf8(REAL_HEADER + """
                
                2026.05.30 17:17,체크,본인035*,빵굼터,,37373238,17050.0,결제확정,일시불,,,,
                2026.04.10 21:49,체크,본인035*,지마켓,,29414627,-10320.0,거래취소,일시불,,,,취소
                ,,,총 177건,,,2004068.0,,,,,,
                """));

        // 금액은 6번 컬럼이다. 비어 있는 해외이용금액(11번)을 잡으면 전부 금액 형식 오류가 된다.
        assertEquals(1, result.rows().size());
        assertEquals("빵굼터", result.rows().get(0).merchant());
        assertEquals(17050, result.rows().get(0).amount());
        assertEquals(OffsetDateTime.parse("2026-05-30T17:17+09:00"), result.rows().get(0).occurredAt());
        assertEquals(List.of("취소·환불 거래", "날짜 형식 오류"),
                result.skipped().stream().map(SkippedRow::reason).toList());
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
    void 따옴표_안의_쉼표는_가맹점명으로_남는다() {
        ParseResult result = parser.parse(utf8("""
                거래일시,가맹점명,금액
                2026-08-25 20:22,"커피,빵집",4500
                """));

        assertEquals("커피,빵집", result.rows().get(0).merchant());
        assertEquals(4500, result.rows().get(0).amount());
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
        byte[] withBom = ("\uFEFF" + """
                거래일시,가맹점명,금액
                2026-08-25 20:22,○○마트,12000
                """).getBytes(StandardCharsets.UTF_8);

        ParseResult result = parser.parse(withBom);

        assertEquals(1, result.rows().size());
        assertEquals("○○마트", result.rows().get(0).merchant());
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
