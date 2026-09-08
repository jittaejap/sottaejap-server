package kr.sottaejap.server.rules.candidate;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 기준선 중앙값 (E-62 ④). */
class MedianTest {

    @Test
    void 홀수_표본은_가운데_값이다() {
        assertEquals(2.0, Median.of(List.of(3, 1, 2)));
    }

    @Test
    void 짝수_표본은_가운데_두_값의_평균이다() {
        assertEquals(2.5, Median.of(List.of(1, 2, 3, 4)));
    }

    @Test
    void 표본이_한_개면_그_값이다() {
        assertEquals(7000.0, Median.of(List.of(7000)));
    }

    @Test
    void 빈_목록은_계산을_거부한다() {
        assertThrows(IllegalArgumentException.class, () -> Median.of(List.of()));
    }

    @Test
    void 입력_리스트를_바꾸지_않는다() {
        List<Integer> values = new ArrayList<>(List.of(3, 1, 2));
        Median.of(values);
        assertEquals(List.of(3, 1, 2), values);
    }

    @Test
    void 같은_값_집합이면_순서가_달라도_같은_결과다() {
        assertEquals(Median.of(List.of(8000, 12000, 10000, 9000, 11000)),
                Median.of(List.of(12000, 11000, 10000, 9000, 8000)));
    }
}
