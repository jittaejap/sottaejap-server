package kr.sottaejap.server.rules.cluster;

import kr.sottaejap.server.common.enums.TimeSlot;
import kr.sottaejap.server.rules.RuleParams;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 묶음 키 조합 (② · B-10 · E-58 · E-59).
 *
 * <p>키는 네 자리 `카테고리|시간대|목적|동행인`이다. 시간대 자리는 카테고리가
 * `rules.cluster.meal-categories`에 들었거나 미분류(`기타`)일 때만 채운다 (E-58) — 그 외에는 비운다 (B-10).
 * 목적·동행인이 없으면 그 자리도 비운다.
 *
 * <p>상위(롤업) 키는 뒤 두 자리를 비운 `카테고리|시간대||`다 (E-59).
 * 목적·동행인이 둘 다 없는 리프는 상위 키와 글자가 같아진다 — 그래서
 * {@link #isParentKey(String)}가 그 리프에도 {@code true}를 준다. 회고가 붙은 거래에
 * 목적·동행인이 하나도 없으면 그 묶음은 애초에 상위 묶음과 같은 대상이므로 나누지 않는다.
 * {@link ClusterEvaluation#isLeaf()}도 같은 판정을 쓴다.
 */
public final class ClusterKeyRule {

    /** 키 구분자. 카테고리·목적·동행인 값에 이 글자가 들어가면 안 된다. */
    public static final String SEPARATOR = "|";

    /** 카테고리 미기재 기본값 (04 §4 · 06 #11). 받은 CSV 3종이 모두 카테고리를 주지 않는다. */
    public static final String UNCATEGORIZED = "기타";

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile(Pattern.quote(SEPARATOR));

    /** 키 자리 수 — 카테고리·시간대·목적·동행인. */
    private static final int KEY_PARTS = 4;

    private ClusterKeyRule() {
    }

    /**
     * 리프 묶음 키를 만든다. 시간대는 {@link #includesTimeSlot(String, RuleParams)}일 때만,
     * 그리고 {@code timeSlot}이 있을 때만 채운다. {@code purpose}·{@code companion}이 null이면 그 자리를 비운다.
     */
    public static String leafKey(String category, TimeSlot timeSlot, String purpose, String companion,
                                 RuleParams params) {
        String resolvedCategory = resolveCategory(category);
        return resolvedCategory
                + SEPARATOR + timeSlotPart(resolvedCategory, timeSlot, params)
                + SEPARATOR + blankIfNull(purpose)
                + SEPARATOR + blankIfNull(companion);
    }

    /** 상위(롤업) 묶음 키 `카테고리|시간대||` — 목적·동행인을 비운다 (E-59). 시간대 규칙은 리프와 같다. */
    public static String parentKey(String category, TimeSlot timeSlot, RuleParams params) {
        String resolvedCategory = resolveCategory(category);
        return resolvedCategory
                + SEPARATOR + timeSlotPart(resolvedCategory, timeSlot, params)
                + SEPARATOR + SEPARATOR;
    }

    /** 리프 키에서 상위 키를 뽑는다 — 앞 두 자리를 그대로 두고 뒤 두 자리를 비운다 (E-59). */
    public static String parentKeyOf(String leafKey) {
        String[] parts = SEPARATOR_PATTERN.split(leafKey, -1);
        if (parts.length != KEY_PARTS) {
            throw new IllegalArgumentException("묶음 키는 `카테고리|시간대|목적|동행인` 네 자리여야 한다: " + leafKey);
        }
        return parts[0] + SEPARATOR + parts[1] + SEPARATOR + SEPARATOR;
    }

    /**
     * 시간대를 키에 넣는 카테고리인지 (B-10 · E-58).
     * `rules.cluster.meal-categories` 목록에 있거나 미분류(`기타`)이면 true다.
     * 목록 항목과 카테고리는 앞뒤 공백을 떼고 정확히 비교한다 — 한글이라 대소문자 구분은 뜻이 없다.
     */
    public static boolean includesTimeSlot(String category, RuleParams params) {
        List<String> mealCategories = RuleParams.require(
                RuleParams.require(params.cluster(), "rules.cluster.meal-categories").mealCategories(),
                "rules.cluster.meal-categories");
        String resolvedCategory = resolveCategory(category).trim();
        if (UNCATEGORIZED.equals(resolvedCategory)) {
            return true;
        }
        return mealCategories.stream()
                .anyMatch(mealCategory -> mealCategory != null && mealCategory.trim().equals(resolvedCategory));
    }

    /** 키가 `||`로 끝나면 상위 묶음이다 (E-59). */
    public static boolean isParentKey(String clusterKey) {
        return clusterKey.endsWith(SEPARATOR + SEPARATOR);
    }

    /** 카테고리가 없으면 미분류로 본다 — 파서가 `기타`를 채우지만 여기서도 막는다 (04 §4). */
    private static String resolveCategory(String category) {
        return category == null ? UNCATEGORIZED : category;
    }

    /** 시간대를 쓰지 않는 카테고리이거나 시간대를 모르면 자리를 비운다 (예외로 만들지 않는다). */
    private static String timeSlotPart(String category, TimeSlot timeSlot, RuleParams params) {
        if (timeSlot == null || !includesTimeSlot(category, params)) {
            return "";
        }
        return timeSlot.name();
    }

    private static String blankIfNull(String value) {
        return value == null ? "" : value;
    }
}
