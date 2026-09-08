package kr.sottaejap.server.transaction.parser;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse.SkippedRow;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 카드사 CSV에서 3사 공통 컬럼(거래일시 · 가맹점 · 금액)만 읽는다 (04 §4).
 *
 * <p>카드사별 헤더 문자열이 확정되지 않아(06 #11) 컬럼을 이름 일치가 아니라 <b>키워드 포함</b>으로 찾는다.
 * KB·하나·신한이 각각 "이용일시" · "거래일시" · "이용하신곳"처럼 달라도 같은 코드로 읽힌다.
 * 카테고리는 있으면 원본을 담고 없으면 비운다 — 3사 통합 매핑표가 나오기 전에는 지어내지 않는다.
 *
 * <p>계산·판정은 하지 않는다. 시간대 분류만 {@code TimeSlot.from}에 맡긴다.
 */
@Component
public class TransactionCsvParser {

    /** UTF-8 BOM. 눈에 보이지 않는 글자라 이름을 붙여 둔다 — 지우면 모든 파일의 첫 글자가 잘린다. */
    private static final char BOM = '\uFEFF';

    /** 머리글 앞에 제목·조회조건 줄이 붙는 내보내기가 많다. 앞쪽 몇 줄 안에서 머리글을 찾는다. */
    private static final int HEADER_SEARCH_LIMIT = 30;

    /** 앞에 오는 키워드를 먼저 맞춘다 — 짧은 것을 뒤에 둬야 "거래일시"가 "거래일"보다 우선한다. */
    private static final List<String> DATE_TIME_KEYWORDS =
            List.of("거래일시", "이용일시", "승인일시", "사용일시", "매출일시",
                    "거래일자", "이용일자", "승인일자", "거래날짜",
                    "거래일", "이용일", "승인일", "매출일", "일시", "날짜");
    private static final List<String> MERCHANT_KEYWORDS =
            List.of("가맹점명", "이용가맹점", "이용하신곳", "가맹점", "사용처", "상호명", "상호", "내용", "적요");
    private static final List<String> AMOUNT_KEYWORDS =
            List.of("이용금액", "승인금액", "거래금액", "사용금액", "결제금액", "금액");
    private static final List<String> CATEGORY_KEYWORDS =
            List.of("가맹점업종", "업종", "카테고리", "분류");

    private static final DateTimeFormatter[] DATE_TIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-M-d H:m:s"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:m"),
    };
    private static final DateTimeFormatter DATE_ONLY_FORMAT = DateTimeFormatter.ofPattern("yyyy-M-d");
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
     * @throws IllegalArgumentException 머리글을 찾지 못한 경우 — 호출자가 PARSE_FAILED로 바꾼다.
     */
    public ParseResult parse(byte[] content) {
        List<String> lines = decode(content).lines().toList();
        int headerIndex = findHeaderIndex(lines);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("거래일시·금액 컬럼이 있는 머리글을 찾지 못했습니다.");
        }

        List<String> headers = splitCsvLine(lines.get(headerIndex));
        int dateTimeColumn = findColumn(headers, DATE_TIME_KEYWORDS);
        int merchantColumn = findColumn(headers, MERCHANT_KEYWORDS);
        int amountColumn = findColumn(headers, AMOUNT_KEYWORDS);
        int categoryColumn = findColumn(headers, CATEGORY_KEYWORDS);
        if (merchantColumn < 0) {
            throw new IllegalArgumentException("가맹점명 컬럼을 찾지 못했습니다.");
        }

        List<ParsedRow> rows = new ArrayList<>();
        List<SkippedRow> skipped = new ArrayList<>();
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            int lineNumber = i + 1;
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitCsvLine(line);
            String reason = readRow(cells, dateTimeColumn, merchantColumn, amountColumn, categoryColumn, lineNumber, rows);
            if (reason != null) {
                skipped.add(new SkippedRow(lineNumber, reason));
            }
        }
        return new ParseResult(rows, skipped);
    }

    /** 성공하면 rows에 담고 null을, 실패하면 건너뛴 사유를 돌려준다. */
    private String readRow(List<String> cells, int dateTimeColumn, int merchantColumn, int amountColumn,
                           int categoryColumn, int lineNumber, List<ParsedRow> rows) {
        String rawDateTime = cellAt(cells, dateTimeColumn);
        String merchant = cellAt(cells, merchantColumn);
        String rawAmount = cellAt(cells, amountColumn);
        if (merchant.isBlank()) {
            return "가맹점명 없음";
        }

        String normalizedDateTime = normalizeDateTime(rawDateTime);
        OffsetDateTime occurredAt = parseDateTime(normalizedDateTime);
        if (occurredAt == null) {
            // 04 §4: 시간 정보가 없으면 시간대를 나눌 수 없어 파싱 실패로 처리한다.
            return hasDateOnly(normalizedDateTime) ? "시간 정보 없음" : "날짜 형식 오류";
        }

        Integer amount = parseAmount(rawAmount);
        if (amount == null) {
            return "금액 형식 오류";
        }
        if (amount <= 0) {
            return "취소·환불 거래";
        }

        String sourceCategory = cellAt(cells, categoryColumn);
        rows.add(new ParsedRow(lineNumber, occurredAt, merchant, amount,
                sourceCategory.isBlank() ? null : sourceCategory));
        return null;
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

    private static int findHeaderIndex(List<String> lines) {
        int limit = Math.min(lines.size(), HEADER_SEARCH_LIMIT);
        for (int i = 0; i < limit; i++) {
            List<String> cells = splitCsvLine(lines.get(i));
            if (findColumn(cells, DATE_TIME_KEYWORDS) >= 0 && findColumn(cells, AMOUNT_KEYWORDS) >= 0) {
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
     * 따옴표 안의 쉼표를 살려 한 줄을 자른다. 가맹점명에 쉼표가 들어가는 내역이 있다.
     *
     * <p>ponytail: 따옴표 안 줄바꿈은 지원하지 않는다. 그런 파일이 나오면 상태 기계로 여러 줄을 잇는다.
     */
    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c != '"') {
                    cell.append(c);
                } else if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else {
                    quoted = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
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
