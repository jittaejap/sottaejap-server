package kr.sottaejap.server.retrospect.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 묶음 이름 템플릿 (E-64 · FR-05-05). AI가 없을 때 이 이름이 화면에 나간다. */
class ClusterNameTemplateTest {

    @Test
    void 시간대가_있으면_시간대_라벨과_카테고리로_짓는다() {
        assertEquals("심야 배달", ClusterNameTemplate.nameFor("배달|NIGHT|충동|혼자"));
    }

    @Test
    void 시간대가_없으면_카테고리와_목적으로_짓는다() {
        assertEquals("교통 필수품", ClusterNameTemplate.nameFor("교통||필수품|혼자"));
    }

    @Test
    void 상위_묶음도_시간대가_있으면_같은_규칙을_쓴다() {
        assertEquals("낮 기타", ClusterNameTemplate.nameFor("기타|DAY||"));
    }

    @Test
    void 시간대도_목적도_없으면_카테고리뿐이다() {
        assertEquals("교통", ClusterNameTemplate.nameFor("교통|||"));
    }

    @Test
    void 열두_자를_넘으면_열두_자로_자른다() {
        assertEquals("가나다라마바사아자차카타",
                ClusterNameTemplate.nameFor("가나다라마바사아자차카타파하||자기계발|"));
        assertEquals(ClusterNameTemplate.MAX_NAME_LENGTH,
                ClusterNameTemplate.nameFor("가나다라마바사아자차카타파하||자기계발|").length());
    }

    /** 12자는 코드 포인트다 (이슈 #20) — 이모지가 열두 번째 자리에 걸려도 서러게이트 페어를 반으로 끊지 않는다. */
    @Test
    void 열두_번째_자리의_이모지를_반으로_자르지_않는다() {
        String name = "심야 배달 카페 🍜🍕"; // length() 13 · 코드 포인트 11 — 종전 substring(0, 12)는 🍕를 반으로 끊었다
        String truncated = ClusterNameTemplate.truncate(name + "🍔🍟"); // 코드 포인트 13

        assertEquals(name, ClusterNameTemplate.truncate(name));
        assertEquals(name + "🍔", truncated);
        assertEquals(ClusterNameTemplate.MAX_NAME_LENGTH, truncated.codePointCount(0, truncated.length()));
    }
}
