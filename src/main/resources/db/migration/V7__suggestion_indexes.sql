-- 제안은 재계산 파생 행이다 (E-81). 회고를 저장할 때마다 도는 재계산이 대상 묶음마다 PROPOSED 한 줄을
-- 두고 제자리 갱신하며, 대상에서 빠지면 그 줄을 지운다. 아래 부분 유일 인덱스가 그 불변식을 DB에서 지킨다.
--
-- ADOPTED · REJECTED는 같은 묶음에 여러 개 쌓일 수 있다(횟수를 고쳐 다시 채택한 이력). 그래서 조건을
-- PROPOSED로 좁힌다 — 전체 유일로 걸면 채택 이력이 남는 순간 새 제안을 만들 수 없다.
CREATE UNIQUE INDEX uq_suggestions_proposed
    ON suggestions (behavior_id) WHERE status = 'PROPOSED';

-- suggestions에는 user_id가 없다 (04 §1). 사용자별 조회가 전부 behavior_clusters 조인이라
-- 조인 키에 인덱스가 없으면 목록 조회가 매번 전체 스캔이 된다.
CREATE INDEX ix_suggestions_behavior ON suggestions (behavior_id);

-- GET /goals가 목표마다 ADOPTED 제안의 expectedSaving을 합산한다 (E-83).
CREATE INDEX ix_suggestions_goal ON suggestions (goal_id) WHERE goal_id IS NOT NULL;
