package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.retrospect.domain.Retrospect;
import kr.sottaejap.server.retrospect.repository.RetrospectRepository;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.dto.TransactionAiView;
import kr.sottaejap.server.transaction.dto.TransactionListQuery;
import kr.sottaejap.server.transaction.dto.TransactionListResponse;
import kr.sottaejap.server.transaction.dto.TransactionView;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse.SkippedRow;
import kr.sottaejap.server.transaction.parser.TransactionFileParser;
import kr.sottaejap.server.transaction.parser.TransactionFileParser.ParsedRow;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    /** 아직 카테고리 통합 매핑표(06 #11)가 없다. 원본이 없으면 지어내지 않고 기타로 둔다 (04 §4). */
    private static final String DEFAULT_CATEGORY = "기타";
    private static final int DEFAULT_AI_PAGE_SIZE = 100;
    private static final int MAX_AI_PAGE_SIZE = 1000;
    /** 05 §2 `GET /transactions` — size 상한. 넘으면 400이 아니라 100으로 자른다 (E-93). */
    private static final int MAX_LIST_PAGE_SIZE = 100;

    // V1 스키마의 컬럼 길이. 긴 셀 하나 때문에 업로드 전체가 500이 되지 않도록 여기서 자른다.
    private static final int MERCHANT_MAX_LENGTH = 255;
    private static final int CATEGORY_MAX_LENGTH = 50;
    private static final int SOURCE_CATEGORY_MAX_LENGTH = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionFileParser parser;
    private final RetrospectRepository retrospectRepository;

    @Override
    @Transactional
    public TransactionUploadResponse upload(long userId, MultipartFile file) {
        TransactionFileParser.ParseResult parsed = parse(file);

        // 재업로드 중복은 저장 전에 걸러낸다 (04 §4). 같은 파일 안의 중복도 같은 Set이 잡는다.
        Set<String> knownHashes = new HashSet<>(transactionRepository.findImportHashesByUserId(userId));
        List<SkippedRow> skipped = new ArrayList<>(parsed.skipped());
        List<Transaction> imported = new ArrayList<>();
        for (ParsedRow row : parsed.rows()) {
            String importHash = importHash(userId, row);
            if (!knownHashes.add(importHash)) {
                skipped.add(new SkippedRow(row.line(), "이미 등록된 거래"));
                continue;
            }
            imported.add(Transaction.of(userId, row.occurredAt(),
                    truncate(row.merchant(), MERCHANT_MAX_LENGTH), row.amount(),
                    truncate(category(row), CATEGORY_MAX_LENGTH),
                    truncate(row.sourceCategory(), SOURCE_CATEGORY_MAX_LENGTH),
                    importHash));
        }
        transactionRepository.saveAll(imported);

        skipped.sort(Comparator.comparingInt(SkippedRow::row));
        return new TransactionUploadResponse(imported.size(), skipped.size(),
                boundaryDate(imported, true), boundaryDate(imported, false), skipped);
    }

    @Override
    @Transactional(readOnly = true)
    public YearMonth analysisYearMonth(long userId) {
        return transactionRepository.findTopByUserIdOrderByOccurredAtDesc(userId)
                .map(transaction -> YearMonth.from(transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE)))
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public TransactionListResponse list(long userId, TransactionListQuery query) {
        // 1 미만 size와 음수 page는 페이지를 만들 수 없고, from > to는 빈 결과가 아니라 잘못된 요청이다 (05 §2).
        if (query.size() < 1 || query.page() < 0
                || (query.from() != null && query.to() != null && query.from().isAfter(query.to()))) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        int size = Math.min(query.size(), MAX_LIST_PAGE_SIZE);
        Page<Transaction> page = transactionRepository.search(
                userId,
                startOfDay(query.from()),
                endOfDayExclusive(query.to()),
                blankToNull(query.category()),
                query.hasRetrospect(),
                PageRequest.of(query.page(), size));

        // 회고 요약은 이 페이지의 거래에 대해서만 한 번에 읽는다. 거래당 하나(UNIQUE)라 id로 바로 맵을 만든다.
        Map<Long, Retrospect> retrospectByTransactionId = page.isEmpty() ? Map.of()
                : retrospectRepository.findAllByTransactionIdIn(page.map(Transaction::getId).getContent()).stream()
                        .collect(Collectors.toMap(Retrospect::getTransactionId, Function.identity()));
        List<TransactionView> transactions = page.getContent().stream()
                .map(transaction -> TransactionView.of(transaction, retrospectByTransactionId.get(transaction.getId())))
                .toList();
        return new TransactionListResponse(transactions, page.getNumber(), size,
                page.getTotalElements(), page.getTotalPages());
    }

    @Override
    @Transactional(readOnly = true)
    public List<TransactionAiView> findForAi(long userId, LocalDate from, LocalDate to, String category, Integer size) {
        int pageSize = size == null || size <= 0 ? DEFAULT_AI_PAGE_SIZE : Math.min(size, MAX_AI_PAGE_SIZE);
        return transactionRepository.search(userId, startOfDay(from), endOfDayExclusive(to), blankToNull(category),
                        null, PageRequest.of(0, pageSize))
                .getContent()
                .stream()
                .map(TransactionAiView::from)
                .toList();
    }

    /** KST 날짜의 0시. 날짜가 없으면 조건 없음. */
    private static OffsetDateTime startOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }

    /** to는 그날을 포함해야 하므로 다음 날 0시 미만으로 본다. */
    private static OffsetDateTime endOfDayExclusive(LocalDate date) {
        return date == null ? null : date.plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** 05 §2는 CSV와 XLSX를 받는다. 확장자로 읽는 방법만 고르고, 서식은 파서가 머리글로 가른다 (04 §4). */
    private TransactionFileParser.ParseResult parse(MultipartFile file) {
        String name = file.getOriginalFilename();
        String lowerName = name == null ? "" : name.toLowerCase(Locale.ROOT);
        boolean xlsx = lowerName.endsWith(".xlsx");
        if (!xlsx && !lowerName.endsWith(".csv")) {
            throw new BusinessException(CommonErrorCode.INVALID_FILE_FORMAT);
        }
        try {
            byte[] content = file.getBytes();
            return xlsx ? parser.parseXlsx(content) : parser.parseCsv(content);
        } catch (IOException | IllegalArgumentException cannotRead) {
            throw new BusinessException(CommonErrorCode.PARSE_FAILED);
        }
    }

    /** 3사 분류 체계가 서로 달라 통합 매핑표(06 #11) 전까지는 원본을 그대로 둔다. */
    private static String category(ParsedRow row) {
        return row.sourceCategory() == null ? DEFAULT_CATEGORY : row.sourceCategory();
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static LocalDate boundaryDate(List<Transaction> imported, boolean earliest) {
        Comparator<Transaction> byOccurredAt = Comparator.comparing(Transaction::getOccurredAt);
        return imported.stream()
                .min(earliest ? byOccurredAt : byOccurredAt.reversed())
                .map(transaction -> transaction.getOccurredAt().atZoneSameInstant(TimeSlot.ZONE).toLocalDate())
                .orElse(null);
    }

    /** 04 §4 중복 판별 — userId + occurredAt + merchant + amount. */
    private static String importHash(long userId, ParsedRow row) {
        String raw = userId + "|" + row.occurredAt().toInstant() + "|" + row.merchant() + "|" + row.amount();
        return HexFormat.of().formatHex(sha256(raw));
    }

    private static byte[] sha256(String raw) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
