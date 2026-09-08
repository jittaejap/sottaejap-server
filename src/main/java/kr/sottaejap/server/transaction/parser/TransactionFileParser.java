package kr.sottaejap.server.transaction.parser;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse.SkippedRow;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 카드사 내역 파일(CSV · XLSX)에서 3사 공통 컬럼(거래일시 · 가맹점 · 금액)만 읽는다 (04 §4 · 05 §2).
 *
 * <p><b>카드사를 가리지 않는다.</b> 카드사별 매핑표를 두지 않고 머리글의 <b>키워드 포함</b>으로 컬럼을 찾는다.
 * KB·하나·신한이 각각 "이용일시" · "거래일시" · "이용하신곳"처럼 달라도 같은 코드로 읽힌다.
 * 카테고리는 있으면 원본을 담고 없으면 비운다 — 3사 통합 매핑표가 나오기 전에는 지어내지 않는다.
 *
 * <p>계산·판정은 하지 않는다. 시간대 분류만 {@code TimeSlot.from}에 맡긴다.
 */
@Component
public class TransactionFileParser {

    /** UTF-8 BOM. 눈에 보이지 않는 글자라 이름을 붙여 둔다 — 지우면 모든 파일의 첫 글자가 잘린다. */
    private static final char BOM = '﻿';

    /** 머리글 앞에 제목·조회조건 줄이 붙는 내보내기가 많다. 앞쪽 몇 줄 안에서 머리글을 찾는다. */
    private static final int HEADER_SEARCH_LIMIT = 30;

    /** 앞에 오는 키워드를 먼저 맞춘다 — 짧은 것을 뒤에 둬야 "거래일시"가 "거래일"보다 우선한다. */
    private static final List<String> DATE_TIME_KEYWORDS =
            List.of("거래일시", "이용일시", "승인일시", "사용일시", "매출일시",
                    "거래일자", "이용일자", "승인일자", "거래날짜",
                    "거래일", "이용일", "승인일", "매출일", "일시", "날짜");

    /**
     * 날짜와 시각을 다른 칸에 나눠 주는 서식이 있다 — 신한 이용내역서의 {@code 이용일} · {@code 이용시간}.
     * 이 칸을 못 찾으면 그 파일은 04 §4의 "시간 정보 없음"으로 한 건도 안 들어온다.
     */
    private static final List<String> TIME_KEYWORDS =
            List.of("이용시간", "거래시간", "승인시간", "결제시간", "매출시간", "시간");

    private static final List<String> CATEGORY_KEYWORDS =
            List.of("가맹점업종", "업종", "카테고리", "분류");

    /** 통장의 거래 수단 칸. 카드 내역서에는 없다. */
    private static final List<String> METHOD_KEYWORDS = List.of("적요", "거래구분", "거래종류");

    /**
     * 통장에서 소비로 볼 거래 수단 (화이트리스트).
     *
     * <p>블랙리스트가 아니라 화이트리스트다. 통장에는 오픈뱅킹출금 · 전자금융 · FBS출금 · 인터넷입금이체처럼
     * 은행마다 다른 이름이 끝없이 나오는데, 새 이름이 하나 새면 송금이 가맹점 소비로 적재된다.
     * 못 알아본 수단은 들이지 않고 건너뛴 사유로 알린다.
     *
     * <p>{@code 현금IC}(ATM 인출)는 일부러 뺐다. 돈은 나갔지만 가맹점 소비가 아니고 가맹점 칸에 ATM·지점명이 들어온다.
     */
    private static final List<String> CARD_PAYMENT_METHODS = List.of("체크카드", "신용카드");

    /**
     * 파일 종류별 컬럼 규칙 (04 §4 · 06 #11).
     *
     * <p><b>카드사가 아니라 컬럼 구성으로 고른다.</b> 같은 KB라도 카드 이용내역서와 통장 거래내역의
     * 머리글이 전혀 다르다. 파일이 스스로 밝히는 것(머리글)만 믿는다.
     *
     * <p>새 형식이 나오면 여기에 상수를 하나 더 두고 {@link #detect} 한 줄을 늘린다. 파서 본문은
     * 형식을 모른 채로 둔다 — 카드사별 if가 본문에 퍼지면 세 갈래가 각자 달라진다.
     */
    private enum Layout {
        /** 카드 이용내역서. 가맹점명과 이용금액이 있고, 음수 금액은 취소·환불이다. */
        CARD(List.of("가맹점명", "이용가맹점", "이용하신곳", "가맹점", "사용처", "상호명", "상호", "내용", "적요"),
                List.of("이용금액", "승인금액", "거래금액", "사용금액", "결제금액", "금액"),
                "취소·환불 거래"),

        /**
         * 통장 거래내역. {@code 적요}에는 거래 <b>수단</b>("체크카드" · "오픈뱅킹출금")이 들어 있고
         * 실제 상대방은 {@code 보낸분/받는분}에 있다. 적요를 잡으면 모든 행의 가맹점이 같아져
         * 회고가 성립하지 않는다. 지출은 {@code 출금액}뿐이고, 0원은 입금 행이다.
         */
        BANKBOOK(List.of("보낸분/받는분", "받는분", "보낸분", "의뢰인/수취인", "수취인", "가맹점명", "적요"),
                List.of("출금액", "출금금액", "지급액", "출금"),
                "출금 없음 — 입금 행");

        private final List<String> merchantKeywords;
        private final List<String> amountKeywords;
        private final String zeroAmountReason;

        Layout(List<String> merchantKeywords, List<String> amountKeywords, String zeroAmountReason) {
            this.merchantKeywords = merchantKeywords;
            this.amountKeywords = amountKeywords;
            this.zeroAmountReason = zeroAmountReason;
        }

        /** 출금액 계열 컬럼이 있으면 통장이다. 카드 내역에는 그런 컬럼이 없다. */
        private static Layout detect(List<String> headers) {
            return findColumn(headers, BANKBOOK.amountKeywords) >= 0 ? BANKBOOK : CARD;
        }
    }

    /** 머리글 줄을 찾을 때만 쓴다 — 어느 형식이든 금액 컬럼 하나는 있어야 머리글이다. */
    private static final List<String> ANY_AMOUNT_KEYWORDS =
            Stream.concat(Layout.BANKBOOK.amountKeywords.stream(), Layout.CARD.amountKeywords.stream()).toList();

    private static final DateTimeFormatter[] DATE_TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m"),
    };
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");

    /** 엑셀이 시각만 담은 칸에 쓰는 기준일(1899-12-31)의 해. 이 해가 나오면 날짜가 아니라 시각이다. */
    private static final int EXCEL_EPOCH_YEAR = 1899;
    private static final DateTimeFormatter DATE_TIME_CELL = DateTimeFormatter.ofPattern("yyyy-M-d H:m:s");
    private static final DateTimeFormatter TIME_ONLY_CELL = DateTimeFormatter.ofPattern("H:m:s");
    private static final Pattern NUMBER = Pattern.compile("-?\\d+");

    /**
     * 파싱 결과. 저장 가능한 행과 건너뛴 행을 분리해 돌려준다.
     */
    public record ParseResult(List<ParsedRow> rows, List<SkippedRow> skipped) {
    }

    /**
     * 저장 직전의 한 행. {@code line}은 파일의 물리적 줄 번호다.
     */
    public record ParsedRow(int line, OffsetDateTime occurredAt, String merchant, int amount, String sourceCategory) {
    }

    /**
     * 잘라만 놓은 한 줄. {@code line}은 이 레코드가 <b>시작한</b> 물리적 줄 번호다 —
     * 따옴표 안 줄바꿈으로 여러 줄에 걸친 레코드도 사용자가 파일에서 찾을 첫 줄을 가리킨다.
     */
    private record RawRow(int line, List<String> cells) {
    }

    /**
     * 머리글에서 찾아낸 컬럼 위치. 못 찾은 것은 -1이다.
     *
     * @param time   날짜와 시각이 다른 칸에 있는 서식용. 없으면 -1
     * @param method 통장 {@code 적요}. 카드 내역서에는 없어 -1이고, 그때는 수단을 따지지 않는다
     */
    private record Columns(int dateTime, int time, int merchant, int amount, int category, int method) {

        private static Columns of(List<String> headers, Layout layout) {
            int dateTime = findColumn(headers, DATE_TIME_KEYWORDS);
            int time = findColumn(headers, TIME_KEYWORDS);
            int merchant = findColumn(headers, layout.merchantKeywords);
            int method = layout == Layout.BANKBOOK ? findColumn(headers, METHOD_KEYWORDS) : -1;
            return new Columns(dateTime,
                    // "거래일시" 한 칸이 날짜와 시각을 다 담은 경우 자기 자신을 시각 칸으로 잡지 않게 한다.
                    time == dateTime ? -1 : time,
                    merchant,
                    findColumn(headers, layout.amountKeywords),
                    findColumn(headers, CATEGORY_KEYWORDS),
                    // 적요를 가맹점으로 쓰는 통장이면 수단으로는 쓸 수 없다 — 모든 행이 같은 값이 된다.
                    method == merchant ? -1 : method);
        }
    }

    /**
     * CSV를 읽는다. 인코딩 감지와 RFC 4180 자르기는 여기서만 한다.
     *
     * @throws IllegalArgumentException 머리글을 찾지 못한 경우 — 호출자가 PARSE_FAILED로 바꾼다.
     */
    public ParseResult parseCsv(byte[] content) {
        return parseRecords(readRecords(decode(content)));
    }

    /**
     * XLSX를 읽는다 (05 §2). 카드사 웹에서 그대로 내려받은 엑셀 파일이 들어온다.
     *
     * <p>머리글 탐색부터는 CSV와 같은 코드를 탄다. 서식 판정·컬럼 매핑·행 해석이 갈라지면
     * 같은 파일을 CSV로 냈을 때와 XLSX로 냈을 때 결과가 달라진다.
     *
     * @throws IllegalArgumentException 파일을 열 수 없거나 머리글을 찾지 못한 경우
     */
    public ParseResult parseXlsx(byte[] content) {
        return parseRecords(readSheet(content));
    }

    private ParseResult parseRecords(List<RawRow> records) {
        int headerIndex = findHeaderIndex(records);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("거래일시·금액 컬럼이 있는 머리글을 찾지 못했습니다.");
        }

        List<String> headers = records.get(headerIndex).cells();
        Layout layout = Layout.detect(headers);
        Columns columns = Columns.of(headers, layout);
        if (columns.merchant() < 0) {
            throw new IllegalArgumentException("가맹점명 컬럼을 찾지 못했습니다.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        for (RawRow record : records.subList(headerIndex + 1, records.size())) {
            if (isNoise(record.cells(), columns)) {
                continue;
            }
            String reason = readRow(record.cells(), layout, columns, record.line(), rows);
            if (reason != null) {
                skipped.add(new SkippedRow(record.line(), reason));
            }
        }
        return new ParseResult(rows, skipped);
    }

    /** 성공하면 rows에 담고 null을, 실패하면 건너뛴 사유를 돌려준다. */
    private String readRow(List<String> cells, Layout layout, Columns columns, int lineNumber, List<ParsedRow> rows) {
        String merchant = cellAt(cells, columns.merchant());
        if (merchant.isBlank()) {
            return "가맹점명 없음";
        }
        if (columns.method() >= 0 && !isCardPayment(cellAt(cells, columns.method()))) {
            // 통장에는 송금·이체·현금인출이 섞여 있고 상대방 이름이 가맹점처럼 생겼다. 걸러내지 않으면
            // "토스 홍길동" 송금이 가맹점 소비로 남아 회고가 엉뚱한 것을 묻는다.
            return "카드 결제 아님";
        }

        String rawDateTime = cellAt(cells, columns.dateTime());
        OffsetDateTime occurredAt = parseOccurredAt(rawDateTime, cellAt(cells, columns.time()));
        if (occurredAt == null) {
            // 04 §4: 시간 정보가 없으면 시간대를 나눌 수 없어 파싱 실패로 처리한다.
            return hasDateOnly(normalizeDateTime(rawDateTime)) ? "시간 정보 없음" : "날짜 형식 오류";
        }

        Integer amount = parseAmount(cellAt(cells, columns.amount()));
        if (amount == null) {
            return "금액 형식 오류";
        }
        if (amount < 0) {
            return "취소·환불 거래";
        }
        if (amount == 0) {
            // 0원의 뜻이 형식마다 다르다. 통장의 입금 행을 "취소·환불"로 뭉뚱그리면 사용자가
            // 건너뛴 70건을 보고 "내가 환불을 70번 받았나" 하게 된다.
            return layout.zeroAmountReason;
        }

        String sourceCategory = cellAt(cells, columns.category());
        rows.add(new ParsedRow(lineNumber, occurredAt, merchant, amount,
                sourceCategory.isBlank() ? null : sourceCategory));
        return null;
    }

    /**
     * 거래가 아닌 행 — 합계 · 요약 · 여백. 내보내기 꼬리에 흔히 붙는다.
     *
     * <p>건너뛴 목록에도 넣지 않는다. 사용자가 보는 "건너뛴 N건"은 진짜 거래만 세야 뜻이 있다.
     * 날짜 칸이 빈 합계 행({@code ,,,총 177건,,,"2,004,068"})과, 날짜 칸에 글자만 있고 가맹점·금액이
     * 함께 빈 요약 행({@code 정상승인건수,,,110} · {@code 이하 여백})이 여기 걸린다. 빈 줄도 마찬가지다.
     */
    private static boolean isNoise(List<String> cells, Columns columns) {
        if (cellAt(cells, columns.dateTime()).isBlank()) {
            return true;
        }
        return cellAt(cells, columns.merchant()).isBlank() && cellAt(cells, columns.amount()).isBlank();
    }

    private static boolean isCardPayment(String method) {
        String normalized = method.replaceAll("\\s+", "");
        return CARD_PAYMENT_METHODS.stream().anyMatch(normalized::contains);
    }

    /** 04 §4: EUC-KR / UTF-8 자동 감지. UTF-8로 못 읽으면 MS949(EUC-KR 확장)로 본다. */
    private static String decode(byte[] content) {
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException notUtf8) {
            text = new String(content, Charset.forName("MS949"));
        }
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }

    private static int findHeaderIndex(List<RawRow> records) {
        int limit = Math.min(records.size(), HEADER_SEARCH_LIMIT);
        for (int i = 0; i < limit; i++) {
            List<String> cells = records.get(i).cells();
            if (findColumn(cells, DATE_TIME_KEYWORDS) >= 0 && findColumn(cells, ANY_AMOUNT_KEYWORDS) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 머리글에서 컬럼 위치를 찾는다. <b>이름이 정확히 같은 것을 먼저</b> 보고, 없을 때만 포함 관계를 본다.
     *
     * <p>포함부터 보면 `금액`과 `해외이용금액`이 함께 있는 파일에서 비어 있는 `해외이용금액`을 잡는다.
     * 실제 카드 내역에 그 조합이 있어 순서를 이렇게 고정했다.
     */
    private static int findColumn(List<String> headers, List<String> keywords) {
        List<String> normalized = headers.stream().map(h -> h.replaceAll("\\s+", "")).toList();
        for (String keyword : keywords) {
            int exact = normalized.indexOf(keyword);
            if (exact >= 0) {
                return exact;
            }
        }
        for (String keyword : keywords) {
            for (int i = 0; i < normalized.size(); i++) {
                if (normalized.get(i).contains(keyword)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String cellAt(List<String> cells, int index) {
        return index >= 0 && index < cells.size() ? cells.get(index) : "";
    }

    /**
     * RFC 4180으로 파일 전체를 레코드로 자른다.
     *
     * <p>따옴표 안에서는 쉼표도 줄바꿈도 글자다. 신한 이용내역서는 <b>머리글</b>에 줄바꿈을 넣어 보낸다
     * ({@code "포인트\n사용"} · {@code "할부\n기간"}). 한 줄씩 자르면 머리글 한 줄이 물리적 네 줄로
     * 쪼개져 컬럼이 절반만 잡히고 나머지 세 줄이 거래 행으로 둔갑한다.
     */
    private static List<RawRow> readRecords(String text) {
        List<RawRow> records = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordStart = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean crlf = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n';
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else if (c == '\n' || c == '\r') {
                    i += crlf ? 1 : 0;
                    cell.append('\n');
                    line++;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else if (c == '\n' || c == '\r') {
                i += crlf ? 1 : 0;
                cells.add(cell.toString().trim());
                cell.setLength(0);
                records.add(new RawRow(recordStart, List.copyOf(cells)));
                cells.clear();
                recordStart = ++line;
            } else {
                cell.append(c);
            }
        }
        if (!cell.isEmpty() || !cells.isEmpty()) {
            cells.add(cell.toString().trim());
            records.add(new RawRow(recordStart, List.copyOf(cells)));
        }
        return records;
    }

    /**
     * XLSX 첫 시트를 CSV와 같은 모양의 레코드로 바꾼다. 시트가 여럿이면 첫 장만 본다 —
     * 카드사 내려받기 파일은 내역이 첫 장이고, 나머지는 안내·약관이다.
     *
     * <p>셀을 문자열로 되돌려 CSV와 같은 해석기에 넘긴다. 날짜·시각 셀은 <b>표시 서식이 아니라 셀이 든
     * 실제 값</b>으로 읽는다 — 같은 거래를 `2026.08.25` · `25/08/26` · `20260825`로 보여주는 파일들이
     * 서식대로 읽으면 제각기 다르게 파싱된다.
     */
    private static List<RawRow> readSheet(byte[] content) {
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            List<RawRow> records = new ArrayList<>();
            // 물리적 줄 번호를 CSV와 맞춘다 — 사용자가 건너뛴 행을 엑셀에서 찾을 때 보는 행 번호다.
            for (Row row : workbook.getSheetAt(0)) {
                records.add(new RawRow(row.getRowNum() + 1, cellsOf(row)));
            }
            return records;
        } catch (IOException | RuntimeException cannotOpen) {
            // POI는 형식이 어긋나면 자기 런타임 예외를 던진다. 호출자가 PARSE_FAILED로 바꾸도록 맞춰 준다.
            throw new IllegalArgumentException("XLSX 파일을 열지 못했습니다.", cannotOpen);
        }
    }

    private static List<String> cellsOf(Row row) {
        List<String> cells = new ArrayList<>();
        // 빈 행은 getLastCellNum()이 -1이라 그대로 빈 목록이 된다 — isNoise가 걸러낸다.
        for (int i = 0; i < row.getLastCellNum(); i++) {
            cells.add(textOf(row.getCell(i)));
        }
        return List.copyOf(cells);
    }

    private static String textOf(Cell cell) {
        if (cell == null) {
            return "";
        }
        // 수식 칸은 다시 계산하지 않고 엑셀이 저장해 둔 결과를 쓴다. 내역 파일의 수식은 합계 행뿐이다.
        CellType type = cell.getCellType() == CellType.FORMULA
                ? cell.getCachedFormulaResultType()
                : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> numericText(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    private static String numericText(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime value = cell.getLocalDateTimeCellValue();
            if (value.getYear() <= EXCEL_EPOCH_YEAR) {
                // 시각만 든 칸 — 신한 이용내역서의 `이용시간`. 날짜 칸과 합쳐져야 거래 시각이 된다.
                return value.format(TIME_ONLY_CELL);
            }
            // 자정은 시각이 아니라 "시각 없음"으로 본다. 엑셀은 날짜만 든 칸을 00:00:00으로 저장하는데,
            // 이걸 자정 거래로 읽으면 실제로는 시간 정보가 없는 파일이 통째로 NIGHT로 적재된다 (04 §4).
            return value.toLocalTime().equals(LocalTime.MIDNIGHT)
                    ? value.format(DATE_ONLY_FORMAT)
                    : value.format(DATE_TIME_CELL);
        }
        // `12000.0`처럼 소수점이 붙으면 금액·시각 해석이 흔들린다. 정수는 정수로 적는다.
        double number = cell.getNumericCellValue();
        return number == Math.rint(number) ? String.valueOf((long) number) : String.valueOf(number);
    }

    /**
     * 날짜 칸을 읽는다. 날짜만 있고 시각이 옆 칸에 있으면 붙여서 다시 읽는다.
     *
     * <p>신한 이용내역서가 {@code 이용일}(2026.09.03) · {@code 이용시간}(18:15:08)으로 나눠 준다.
     * 붙이지 않으면 04 §4의 "시간 정보 없음"에 걸려 그 파일은 한 건도 안 들어온다.
     */
    private static OffsetDateTime parseOccurredAt(String rawDateTime, String rawTime) {
        OffsetDateTime occurredAt = parseDateTime(normalizeDateTime(rawDateTime));
        if (occurredAt != null || rawTime.isBlank()) {
            return occurredAt;
        }
        return parseDateTime(normalizeDateTime(rawDateTime + " " + rawTime));
    }

    /** `2026.08.25. 20:22` · `2026/8/25 20:22:30` · `2026-08-25T20:22`를 한 모양으로 모은다. */
    private static String normalizeDateTime(String raw) {
        return raw.trim()
                .replace('.', '-')
                .replace('/', '-')
                .replace('T', ' ')
                .replaceAll("\\s+", " ")
                .replaceAll("-+ ", " ")
                .replaceAll("-+$", "");
    }

    private static OffsetDateTime parseDateTime(String normalized) {
        for (DateTimeFormatter format : DATE_TIME_FORMATS) {
            try {
                // 국내 카드 내역이라 시각은 한국 시간으로 읽는다. 구간 분류와 같은 기준을 쓴다.
                return LocalDateTime.parse(normalized, format).atZone(TimeSlot.ZONE).toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // 다음 형식으로 넘어간다.
            }
        }
        return null;
    }

    private static boolean hasDateOnly(String normalized) {
        try {
            LocalDate.parse(normalized, DATE_ONLY_FORMAT);
            return true;
        } catch (DateTimeParseException notADate) {
            return false;
        }
    }

    /** 04 §4: `1,200원` → 1200. 취소 표기의 음수도 그대로 읽어 호출자가 걸러낸다. */
    private static Integer parseAmount(String raw) {
        Matcher matcher = NUMBER.matcher(raw.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group());
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }
}
