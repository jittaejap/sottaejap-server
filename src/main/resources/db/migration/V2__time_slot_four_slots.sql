-- 시간대 4구간 반영 (E-50 · 06 R13). 정본: sottaejap-docs/04_데이터모델_ERD.md §1 · 05 §0 v1.9.
-- V1의 CHECK는 MORNING/AFTERNOON/NIGHT 3종이라 DAY·EVENING을 넣을 수 없다.
-- ⚠️ V1 주석과 server/AGENTS.md는 V2를 seed_demo(정민규)로 적어 두었으나,
--    06 액션시트 v1.9 R13이 이 변경을 V2로 지정했다. 시드는 다음 번호로 간다.

-- 새 값을 쓰기 전에 옛 CHECK를 먼저 없앤다. 남겨 두면 아래 UPDATE가 쓰는 DAY·EVENING이
-- 23514(transactions_time_slot_check 위반)로 막혀, V1 데이터가 있는 DB에서 기동이 실패한다.
ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_time_slot_check;

-- AFTERNOON만이 아니라 기존 행 전부를 다시 계산한다. 경계가 통째로 바뀌어서,
-- 11:00~11:59는 예전에 MORNING이었지만 E-50부터 DAY다. 규칙과 경계는 TimeSlot.from과 같다 —
-- 시작 시각을 포함하고 끝 시각을 제외하며, 국내 카드 내역이므로 KST로 자른다.
UPDATE transactions
SET time_slot = CASE
        WHEN EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Seoul') < 5  THEN 'NIGHT'
        WHEN EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Seoul') < 11 THEN 'MORNING'
        WHEN EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Seoul') < 17 THEN 'DAY'
        WHEN EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Seoul') < 22 THEN 'EVENING'
        ELSE 'NIGHT'
    END;

ALTER TABLE transactions ADD CONSTRAINT transactions_time_slot_check
    CHECK (time_slot IN ('MORNING', 'DAY', 'EVENING', 'NIGHT'));
