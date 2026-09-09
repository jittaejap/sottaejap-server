package kr.sottaejap.server.transaction.parser;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse.SkippedRow;
import kr.sottaejap.server.transaction.parser.TransactionFileParser.ParseResult;
import kr.sottaejap.server.transaction.parser.TransactionFileParser.ParsedRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

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
class TransactionFileParserTest {

    private final TransactionFileParser parser = new TransactionFileParser();

    @Test
    void 카드사마다_다른_헤더를_키워드로_찾는다() {
        ParseResult kb = parser.parseCsv(utf8("""
                이용일시,이용하신곳,이용금액,업종
                2026.08.25 20:22:30,○○마트,"12,000",유통
                """));
        ParseResult shinhan = parser.parseCsv(utf8("""
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
        ParseResult result = parser.parseCsv(utf8(REAL_CARD_HEADER + """
                
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
        ParseResult result = parser.parseCsv(utf8("""
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
        ParseResult result = parser.parseCsv(utf8("""
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
        ParseResult result = parser.parseCsv(utf8(REAL_BANK_HEADER + """
                
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
        ParseResult result = parser.parseCsv(utf8("""
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
        ParseResult result = parser.parseCsv(utf8("""
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
        ParseResult result = parser.parseCsv(utf8("""
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

        ParseResult result = parser.parseCsv(eucKr);

        assertEquals("○○마트", result.rows().get(0).merchant());
    }

    @Test
    void BOM으로_시작하는_UTF_8_파일도_읽는다() {
        byte[] withBom = ("﻿" + """
                거래일시,가맹점명,금액
                2026-08-25 20:22,○○마트,12000
                """).getBytes(StandardCharsets.UTF_8);

        ParseResult result = parser.parseCsv(withBom);

        assertEquals(1, result.rows().size());
        assertEquals("○○마트", result.rows().get(0).merchant());
    }

    @Test
    void CRLF로_저장된_파일도_줄_번호가_어긋나지_않는다() {
        ParseResult result = parser.parseCsv(utf8("거래일시,가맹점명,금액\r\n2026-08-25 20:22,○○마트,12000\r\n"));

        assertEquals(1, result.rows().size());
        assertEquals(2, result.rows().get(0).line());
    }

    @Test
    void 머리글을_못_찾으면_예외로_알린다() {
        assertThrows(IllegalArgumentException.class, () -> parser.parseCsv(utf8("아무 내용,없음\n1,2\n")));
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
        ParsedRow first = parser.parseCsv(utf8("거래일시,가맹점명,금액\n2026.08.25 20:22:00,○○마트,12000\n")).rows().get(0);
        ParsedRow second = parser.parseCsv(utf8("이용일시,이용하신곳,이용금액\n2026-08-25 20:22,○○마트,\"12,000\"\n")).rows().get(0);

        assertEquals(first.occurredAt().toInstant(), second.occurredAt().toInstant());
        assertTrue(first.occurredAt().toString().endsWith("+09:00"));
    }

    private TimeSlot slotAt(String dateTime) {
        OffsetDateTime occurredAt =
                parser.parseCsv(utf8("거래일시,가맹점명,금액\n" + dateTime + ",○○마트,12000\n")).rows().get(0).occurredAt();
        return TimeSlot.from(occurredAt);
    }

    @Test
    void XLSX도_CSV와_같은_규칙으로_읽힌다() {
        byte[] xlsx = workbook(sheet -> {
            header(sheet, 0, "이용일시", "이용하신곳", "이용금액", "업종");
            Row row = sheet.createRow(1);
            dateTimeCell(row, 0, LocalDateTime.of(2026, 8, 25, 20, 22, 30));
            row.createCell(1).setCellValue("○○마트");
            row.createCell(2).setCellValue(12000);
            row.createCell(3).setCellValue("유통");
        });

        ParseResult result = parser.parseXlsx(xlsx);

        assertEquals(1, result.rows().size());
        ParsedRow first = result.rows().get(0);
        assertEquals(OffsetDateTime.parse("2026-08-25T20:22:30+09:00"), first.occurredAt());
        assertEquals("○○마트", first.merchant());
        assertEquals(12000, first.amount());
        assertEquals("유통", first.sourceCategory());
        // 엑셀 2행 = 파일에서 사용자가 보는 행 번호.
        assertEquals(2, first.line());
    }

    @Test
    void XLSX의_날짜와_시각이_다른_칸에_있어도_붙여_읽는다() {
        byte[] xlsx = workbook(sheet -> {
            header(sheet, 0, "이용일", "이용시간", "가맹점명", "이용금액");
            Row row = sheet.createRow(1);
            // 날짜만 든 칸은 엑셀이 00:00:00으로 저장한다. 자정 거래로 읽으면 안 된다.
            dateCell(row, 0, LocalDateTime.of(2026, 9, 3, 0, 0));
            timeCell(row, 1, "18:15:08");
            row.createCell(2).setCellValue("○○커피");
            row.createCell(3).setCellValue(4500);
        });

        ParseResult result = parser.parseXlsx(xlsx);

        assertEquals(1, result.rows().size());
        assertEquals(OffsetDateTime.parse("2026-09-03T18:15:08+09:00"), result.rows().get(0).occurredAt());
    }

    @Test
    void XLSX에_시각이_전혀_없으면_04_4대로_건너뛴다() {
        byte[] xlsx = workbook(sheet -> {
            header(sheet, 0, "이용일", "가맹점명", "이용금액");
            Row row = sheet.createRow(1);
            dateCell(row, 0, LocalDateTime.of(2026, 9, 3, 0, 0));
            row.createCell(1).setCellValue("○○커피");
            row.createCell(2).setCellValue(4500);
        });

        ParseResult result = parser.parseXlsx(xlsx);

        assertTrue(result.rows().isEmpty());
        assertEquals(List.of(new SkippedRow(2, "시간 정보 없음")), result.skipped());
    }

    @Test
    void XLSX가_아닌_바이트는_예외로_알린다() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parseXlsx(utf8("거래일시,가맹점명,금액\n2026-08-25 20:22,○○마트,12000\n")));
    }

    /** 05 §2 행수 상한 — 상한까지는 그대로 읽고, 한 행이라도 넘으면 해석 전에 거절한다 (06 R28). */
    @Test
    void 머리글_아래_행이_상한을_넘으면_해석하지_않고_거절한다() {
        assertEquals(TransactionFileParser.MAX_ROWS, parser.parseCsv(utf8(csvRows(TransactionFileParser.MAX_ROWS))).rows().size());

        assertThrows(TransactionFileParser.TooManyRowsException.class,
                () -> parser.parseCsv(utf8(csvRows(TransactionFileParser.MAX_ROWS + 1))));
    }

    /**
     * XLSX도 같은 지점에서 센다 — 형식마다 상한이 다르면 같은 내역을 CSV로 냈을 때와 결과가 갈린다.
     *
     * <p>한 행 넘긴 파일은 선계수(물리적 행 문턱)에 걸리지 않는다 — 머리글 탐색 여유(30줄)만큼 느슨하게
     * 잡아야 상한 안의 파일이 새로 거절되지 않기 때문이다. 그 구간은 워크북을 열어 정확히 센다.
     */
    @Test
    void XLSX도_같은_상한으로_거절한다() {
        TransactionFileParser.TooManyRowsException rejected = assertThrows(
                TransactionFileParser.TooManyRowsException.class,
                () -> parser.parseXlsx(oneSheetWorkbook(TransactionFileParser.MAX_ROWS + 1)));

        assertTrue(rejected.getMessage().startsWith("머리글 아래 행이"), rejected.getMessage());
    }

    /**
     * 이슈 #58 — 상한을 크게 넘긴 파일은 워크북을 <b>열기 전에</b> 걸러야 한다. 열고 나서 세면
     * {@code OutOfMemoryError}가 나 400이 아니라 500이 된다. 두 경로가 내는 예외는 같은 종류라
     * <b>메시지</b>로만 갈린다 — "첫 시트의 물리적 행"이면 열기 전, "머리글 아래 행"이면 연 뒤다.
     */
    @Test
    void XLSX는_상한을_크게_넘으면_열기_전에_거절한다() {
        TransactionFileParser.TooManyRowsException rejected = assertThrows(
                TransactionFileParser.TooManyRowsException.class,
                () -> parser.parseXlsx(oneSheetWorkbook(TransactionFileParser.MAX_ROWS + 100)));

        assertTrue(rejected.getMessage().startsWith("첫 시트의 물리적 행"), rejected.getMessage());
    }

    /**
     * 이슈 #58 — 선계수는 {@code <row} 다음 <b>바이트</b>로 여는 태그를 가른다. XML 규격에서 태그 이름
     * 뒤의 공백은 {@code #x20 | #x9 | #xD | #xA} 넷 다이므로 띄어쓰기만 인정하면 {@code <row\nr="1">}을
     * 쓴 정상 워크시트가 0행으로 세어진다. 0은 어떤 상한도 넘지 못해 <b>가드가 말없이 꺼지고</b>,
     * 파일은 워크북을 여는 경로로 되돌아가 OOM → 500이 된다.
     *
     * <p>거절 여부만 보면 두 경로가 구별되지 않는다 — 연 뒤에 세는 경로도 결국 400을 낸다.
     * 그래서 여기서도 <b>메시지</b>로 어느 경로가 걸렀는지 확인한다.
     */
    @Test
    void XLSX의_row_태그를_줄바꿈으로_띄워도_열기_전에_센다() {
        byte[] file = withRowTagSeparator(oneSheetWorkbook(TransactionFileParser.MAX_ROWS + 100), "\n");

        TransactionFileParser.TooManyRowsException rejected = assertThrows(
                TransactionFileParser.TooManyRowsException.class, () -> parser.parseXlsx(file));

        assertTrue(rejected.getMessage().startsWith("첫 시트의 물리적 행"), rejected.getMessage());
    }

    /**
     * 뒷장은 첫 장에 <b>더하지 않는다.</b> 읽는 것은 첫 장뿐이라, 안내·약관 장의 줄을 계약 상한에 합치면
     * 상한 안(15,000행)의 내역을 담은 파일이 "거래내역이 너무 많아요"로 거절된다 (PR #63 리뷰).
     */
    @Test
    void XLSX는_뒷장이_있어도_첫_장이_상한_안이면_읽는다() {
        byte[] file = twoSheetWorkbook(15_000, 6_000);

        assertEquals(15_000, parser.parseXlsx(file).rows().size());
    }

    /**
     * 뒷장이 만드는 것은 계약 문제가 아니라 <b>메모리</b> 문제다. {@code XSSFWorkbook}은 첫 장만 쓰더라도
     * 모든 장을 객체 모델로 올리므로, 뒷장에 큰 시트를 숨긴 파일은 열자마자 {@code OutOfMemoryError} →
     * 500이 된다.
     *
     * <p>메모리 문턱이 <b>둘</b>인 이유가 이 두 테스트다. <b>넓은</b> 시트는 행이 적어도 셀이 많아
     * 크기로 걸러야 하고, <b>좁고 긴</b> 시트는 같은 크기에 훨씬 많은 행을 담아 행수로 걸러야 한다 —
     * 하나만 두면 나머지 하나가 그대로 빠져나간다 (PR #63 리뷰 실측 — 500,000행 × 1칸이 30.2MB로
     * 크기 문턱 아래인 채 `XSSFWorkbook`에서 OOM).
     *
     * <p>두 픽스처 모두 첫 장은 세 줄뿐이라 <b>계약 상한에는 걸리지 않는다</b> — 거절이 나온다는 것은
     * 첫 장 행수가 아닌 다른 문턱이 걸렀다는 뜻이고, 어느 쪽인지는 메시지로 가른다.
     */
    @Test
    void XLSX는_열면_힙이_모자랄_크기의_뒷장을_열기_전에_거절한다() {
        byte[] file = workbookWithLargeSecondSheet();

        TransactionFileParser.TooManyRowsException rejected = assertThrows(
                TransactionFileParser.TooManyRowsException.class, () -> parser.parseXlsx(file));

        assertTrue(rejected.getMessage().startsWith("압축을 풀면"), rejected.getMessage());
    }

    @Test
    void XLSX는_크기_문턱_아래여도_뒷장이_길면_열기_전에_거절한다() {
        byte[] file = workbookWithLongSecondSheet();

        TransactionFileParser.TooManyRowsException rejected = assertThrows(
                TransactionFileParser.TooManyRowsException.class, () -> parser.parseXlsx(file));

        assertTrue(rejected.getMessage().startsWith("워크북 전체 물리적 행이"), rejected.getMessage());
    }

    /**
     * zip이 적어 둔 크기는 <b>파일이 스스로 적은 값</b>이라 그대로 믿을 수 없다. 0이라고 적어 두면
     * <b>크기 문턱이 통째로 꺼지고</b>(실측 — 이 파일이 거절되지 않고 그대로 파싱됐다), 그 뒤
     * {@code OPCPackage.open}이 실제 크기를 힙에 푼다. 행수 문턱은 그 자리보다 뒤인 데다 이 파일처럼
     * 행이 적고 칸만 넓으면 걸리지도 않는다. 그래서 상한 안이라고 적은 파일은 흘려보내며 실제로 다시 잰다.
     */
    @Test
    void XLSX는_zip이_적어_둔_크기를_믿지_않는다() {
        byte[] file = withZeroedInflatedSizes(workbookWithLargeSecondSheet());

        TransactionFileParser.TooManyRowsException rejected = assertThrows(
                TransactionFileParser.TooManyRowsException.class, () -> parser.parseXlsx(file));

        assertTrue(rejected.getMessage().startsWith("압축을 풀면"), rejected.getMessage());
    }

    /** 머리글 아래 {@code rows}행이 든 한 장짜리 워크북. */
    private static byte[] oneSheetWorkbook(int rows) {
        return workbook(sheet -> {
            header(sheet, 0, "거래일시", "가맹점명", "금액");
            fill(sheet, 1, rows);
        });
    }

    /** 첫 장에 내역 {@code first}행, 뒷장에 안내 {@code second}행이 든 워크북. 머리글은 첫 장에만 둔다. */
    private static byte[] twoSheetWorkbook(int first, int second) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Sheet transactions = workbook.createSheet("이용내역");
            header(transactions, 0, "거래일시", "가맹점명", "금액");
            fill(transactions, 1, first);
            fill(workbook.createSheet("이용안내"), 0, second);
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException cannotWrite) {
            throw new UncheckedIOException(cannotWrite);
        }
    }

    /** 첫 장은 세 줄, 뒷장은 <b>넓어서</b>(50칸) 압축을 풀면 상한 32MB를 넘는 워크북. */
    private static byte[] workbookWithLargeSecondSheet() {
        return withSecondSheet(12_000, 50);
    }

    /** 첫 장은 세 줄, 뒷장은 <b>좁고 길어서</b>(1칸) 크기는 상한 아래인데 행수 상한 200,000을 넘는 워크북. */
    private static byte[] workbookWithLongSecondSheet() {
        return withSecondSheet(200_100, 1);
    }

    /**
     * 첫 장은 머리글 + 두 줄, 뒷장은 {@code rows}행 × {@code cells}칸인 워크북.
     *
     * <p>메모리를 아끼려고 {@link SXSSFWorkbook}(창 100행)으로 쓴다 — 픽스처를 만드느라 테스트 JVM이
     * 먼저 OOM으로 죽으면 확인하려던 것을 확인하지 못한다.
     */
    private static byte[] withSecondSheet(int rows, int cells) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            Sheet transactions = workbook.createSheet("이용내역");
            header(transactions, 0, "거래일시", "가맹점명", "금액");
            fill(transactions, 1, 2);
            Sheet second = workbook.createSheet("이용안내");
            for (int i = 0; i < rows; i++) {
                Row row = second.createRow(i);
                for (int cell = 0; cell < cells; cell++) {
                    // 같은 글자를 반복하면 압축비가 100배를 넘어 POI가 zip bomb으로 먼저 거절한다.
                    row.createCell(cell).setCellValue("이용안내 " + i + "-" + cell + " 문구가 이어집니다");
                }
            }
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException cannotWrite) {
            throw new UncheckedIOException(cannotWrite);
        }
    }

    /**
     * 중앙 디렉터리 레코드마다 적혀 있는 "압축을 풀면 몇 바이트"를 0으로 고친다. 로컬 헤더와 압축된
     * 데이터는 그대로라 POI는 이 파일을 종전대로 읽는다 — 거짓인 것은 적힌 크기뿐이다.
     */
    private static byte[] withZeroedInflatedSizes(byte[] xlsx) {
        byte[] patched = xlsx.clone();
        ByteBuffer zip = ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN);
        int end = endRecord(zip, patched.length);
        for (int record = zip.getInt(end + 16); record < end && zip.getInt(record) == 0x02014b50; ) {
            zip.putInt(record + 24, 0);
            record += 46 + (zip.getShort(record + 28) & 0xFFFF)   // 이름
                    + (zip.getShort(record + 30) & 0xFFFF)        // 확장 필드
                    + (zip.getShort(record + 32) & 0xFFFF);       // 주석
        }
        return patched;
    }

    /** zip 끝 기록(EOCD)의 자리. 기록은 최소 22바이트이고, 그 16바이트째에 중앙 디렉터리 시작 위치가 적혀 있다. */
    private static int endRecord(ByteBuffer zip, int length) {
        for (int i = length - 22; i >= 0; i--) {
            if (zip.getInt(i) == 0x06054b50) {
                return i;
            }
        }
        throw new IllegalStateException("zip 끝 기록을 찾지 못했습니다");
    }

    /** {@code from}행부터 {@code rows}줄을 같은 거래로 채운다. */
    private static void fill(Sheet sheet, int from, int rows) {
        for (int i = 0; i < rows; i++) {
            Row row = sheet.createRow(from + i);
            row.createCell(0).setCellValue("2026-08-25 20:22");
            row.createCell(1).setCellValue("○○마트");
            row.createCell(2).setCellValue(12000);
        }
    }

    /**
     * 워크시트 XML의 {@code <row } 뒤 공백을 다른 공백 문자로 바꾼다. POI는 언제나 띄어쓰기를 쓰지만
     * XML 규격은 넷을 다 허용하므로, 다른 도구가 쓴 파일을 흉내 내려면 zip을 다시 써야 한다.
     */
    private static byte[] withRowTagSeparator(byte[] xlsx, String separator) {
        try {
            Path original = Files.createTempFile("fixture", ".xlsx");
            Files.write(original, xlsx);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipFile zip = new ZipFile(original.toFile());
                 ZipOutputStream rewritten = new ZipOutputStream(bytes)) {
                for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements(); ) {
                    ZipEntry entry = entries.nextElement();
                    byte[] part = zip.getInputStream(entry).readAllBytes();
                    if (entry.getName().startsWith("xl/worksheets/")) {
                        part = new String(part, StandardCharsets.UTF_8)
                                .replace("<row ", "<row" + separator)
                                .getBytes(StandardCharsets.UTF_8);
                    }
                    rewritten.putNextEntry(new ZipEntry(entry.getName()));
                    rewritten.write(part);
                    rewritten.closeEntry();
                }
            }
            Files.delete(original);
            return bytes.toByteArray();
        } catch (IOException cannotRewrite) {
            throw new UncheckedIOException(cannotRewrite);
        }
    }

    private static String csvRows(int rows) {
        StringBuilder csv = new StringBuilder("거래일시,가맹점명,금액\n");
        for (int i = 0; i < rows; i++) {
            csv.append("2026-08-25 20:22,○○마트,12000\n");
        }
        return csv.toString();
    }

    private static byte[] utf8(String csv) {
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    /** 픽스처를 파일로 두지 않고 여기서 만든다 — 셀 종류(문자·숫자·날짜)가 코드에 드러나야 한다. */
    private static byte[] workbook(Consumer<Sheet> fill) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            fill.accept(workbook.createSheet("이용내역"));
            workbook.write(bytes);
            return bytes.toByteArray();
        } catch (IOException cannotWrite) {
            throw new UncheckedIOException(cannotWrite);
        }
    }

    private static void header(Sheet sheet, int rowIndex, String... names) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < names.length; i++) {
            row.createCell(i).setCellValue(names[i]);
        }
    }

    private static void dateTimeCell(Row row, int column, LocalDateTime value) {
        styledCell(row, column, "yyyy-mm-dd hh:mm:ss").setCellValue(value);
    }

    private static void dateCell(Row row, int column, LocalDateTime value) {
        styledCell(row, column, "yyyy-mm-dd").setCellValue(value);
    }

    /** 시각만 든 칸은 엑셀에서 하루의 비율(0~1)이다. 신한 이용내역서의 `이용시간`이 이 모양이다. */
    private static void timeCell(Row row, int column, String time) {
        styledCell(row, column, "hh:mm:ss").setCellValue(DateUtil.convertTime(time));
    }

    private static Cell styledCell(Row row, int column, String format) {
        Workbook workbook = row.getSheet().getWorkbook();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat(format));
        Cell cell = row.createCell(column);
        cell.setCellStyle(style);
        return cell;
    }
}
