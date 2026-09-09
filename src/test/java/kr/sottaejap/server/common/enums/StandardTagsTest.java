package kr.sottaejap.server.common.enums;

import kr.sottaejap.server.common.exception.BusinessException;
import kr.sottaejap.server.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 표준 태그 정규화 (E-20). 표기가 달라도 같은 태그면 정본으로 받고, 태그 밖 문자열은 여전히 거절한다.
 */
class StandardTagsTest {

    @ParameterizedTest
    @CsvSource({
            "휴식·취미, 휴식·취미",
            "'휴식 · 취미', 휴식·취미",
            "'만남 · 사교', 만남·사교",
            "'  식사  ', 식사",
    })
    void 공백만_다른_표기는_정본_목적으로_받는다(String given, String expected) {
        assertEquals(expected, StandardTags.normalizePurpose(given));
    }

    @Test
    void 동행인도_같다() {
        assertEquals("혼자", StandardTags.normalizeCompanion(" 혼자 "));
        // 비분리 공백(U+00A0)도 지운다 — 복사·붙여넣기로 섞여 들어온다.
        assertEquals("가족", StandardTags.normalizeCompanion("\u00A0가족"));
    }

    @Test
    void 표준_태그_밖_자유_문자열은_null이다() {
        assertNull(StandardTags.normalizePurpose("야식"));
        assertNull(StandardTags.normalizeCompanion("반려견"));
        // 공백을 지워도 태그가 되지 않는 값이다. 정규화가 새 태그를 만들어 내지 않는다.
        assertNull(StandardTags.normalizePurpose("휴 식 취 미"));
    }

    @Test
    void null은_미확정이라_그대로_통과한다() {
        assertNull(StandardTags.normalizePurpose(null));
        assertNull(StandardTags.requirePurpose(null));
        assertNull(StandardTags.requireCompanion(null));
    }

    @Test
    void require는_태그_밖이면_400_INVALID_TAG다() {
        BusinessException purpose = assertThrows(BusinessException.class, () -> StandardTags.requirePurpose("야식"));
        BusinessException companion = assertThrows(BusinessException.class,
                () -> StandardTags.requireCompanion("반려견"));

        assertEquals(CommonErrorCode.INVALID_TAG, purpose.getErrorCode());
        assertEquals(CommonErrorCode.INVALID_TAG, companion.getErrorCode());
    }
}
