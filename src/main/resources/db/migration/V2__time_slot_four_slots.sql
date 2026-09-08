-- 시간대 4구간 반영 (E-50 · 06 R13). 정본: sottaejap-docs/04_데이터모델_ERD.md §1 · 05 §0 v1.9.
-- V1의 CHECK는 MORNING/AFTERNOON/NIGHT 3종이라 DAY·EVENING을 넣을 수 없다.
-- ⚠️ V1 주석과 server/AGENTS.md는 V2를 seed_demo(정민규)로 적어 두었으나,
--    06 액션시트 v1.9 R13이 이 변경을 V2로 지정했다. 시드는 다음 번호로 간다.

-- AFTERNOON(12~22)은 DAY(~17)와 EVENING(17~)으로 갈린다. 거래 시각으로 다시 계산한다.
UPDATE transactions
SET time_slot = CASE
        WHEN EXTRACT(HOUR FROM occurred_at AT TIME ZONE 'Asia/Seoul') >= 17 THEN 'EVENING'
        ELSE 'DAY'
    END
WHERE time_slot = 'AFTERNOON';

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_time_slot_check;
ALTER TABLE transactions ADD CONSTRAINT transactions_time_slot_check
    CHECK (time_slot IN ('MORNING', 'DAY', 'EVENING', 'NIGHT'));
