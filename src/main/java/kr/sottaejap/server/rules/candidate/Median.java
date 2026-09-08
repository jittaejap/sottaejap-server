package kr.sottaejap.server.rules.candidate;

import java.util.ArrayList;
import java.util.List;

/**
 * 기준선 중앙값 (E-62 ④). 평균이 아니라 중앙값을 쓰는 이유는 큰 금액 한 건이 기준선을 끌어올리지 않게 하기 위해서다.
 *
 * <p>결정론(E-18): 입력 순서가 달라도 정렬 후 계산하므로 같은 값 집합이면 같은 결과가 나온다.
 */
public final class Median {

    private Median() {
    }

    /**
     * 오름차순 정렬 후 중앙값. 표본 수가 짝수면 가운데 두 값의 평균이다. 입력 리스트는 바꾸지 않는다.
     *
     * @throws IllegalArgumentException 목록이 비어 있을 때 — 중앙값이 없는데 0을 돌려주면 이상치 판정이 무너진다
     */
    public static double of(List<Integer> values) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("중앙값을 구할 표본이 없다");
        }
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(null);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + (double) sorted.get(middle)) / 2;
    }
}
