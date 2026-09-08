package kr.sottaejap.server.transaction.service;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import kr.sottaejap.server.transaction.domain.Transaction;
import kr.sottaejap.server.transaction.dto.TransactionAiView;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse;
import kr.sottaejap.server.transaction.dto.TransactionUploadResponse.SkippedRow;
import kr.sottaejap.server.transaction.parser.TransactionCsvParser;
import kr.sottaejap.server.transaction.parser.TransactionCsvParser.ParsedRow;
import kr.sottaejap.server.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransactionServiceImpl implements TransactionService {

    /** 아직 카테고리 통합 매핑표(06 #11)가 없다. 원본이 없으면 지어내지 않고 기타로 둔다 (04 §4). */
    private static final String DEFAULT_CATEGORY = "기타";
    private static final int DEFAULT_AI_PAGE_SIZE = 100;
    private static final int MAX_AI_PAGE_SIZE = 1000;

    // V1 스키마의 컬럼 길이. 긴 셀 하나 때문에 업로드 전체가 500이 되지 않도록 여기서 자른다.
    private static final int MERCHANT_MAX_LENGTH = 255;
    private static final int CATEGORY_MAX_LENGTH = 50;
    private static final int SOURCE_CATEGORY_MAX_LENGTH = 100;

    private final TransactionRepository transactionRepository;
    private final TransactionCsvParser parser;

    @Override
    @Transactional
    public TransactionUploadResponse upload(long userId, MultipartFile file) {
        TransactionCsvParser.ParseResult parsed = parse(file);

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
    public List<TransactionAiView> findForAi(long userId, LocalDate from, LocalDate to, String category, Integer size) {
        int pageSize = size == null || size <= 0 ? DEFAULT_AI_PAGE_SIZE : Math.min(size, MAX_AI_PAGE_SIZE);
        return transactionRepository.search(
                        userId,
                        from == null ? null : from.atStartOfDay(TimeSlot.ZONE).toOffsetDateTime(),
                        // to는 그날을 포함해야 하므로 다음 날 0시 미만으로 본다.
                        to == null ? null : to.plusDays(1).atStartOfDay(TimeSlot.ZONE).toOffsetDateTime(),
                        category == null || category.isBlank() ? null : category,
                        PageRequest.of(0, pageSize))
                .stream()
                .map(TransactionAiView::from)
                .toList();
    }

    private TransactionCsvParser.ParseResult parse(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new BusinessException(CommonErrorCode.INVALID_FILE_FORMAT);
        }
        try {
            return parser.parse(file.getBytes());
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
