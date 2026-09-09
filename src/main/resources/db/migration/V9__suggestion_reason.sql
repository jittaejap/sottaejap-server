-- 제안 이유 문장을 AI(ACTION_PLAN)가 쓴 것으로 덮어쓸 자리다 (05 §3 · FR-08-01). 비어 있으면 화면은
-- SuggestionReasonTemplate의 판정 문구를 그대로 보여준다 — AI가 내려가도 제안 목록은 뜬다 (E-38).
--
-- 길이를 제한하지 않는다. 묶음 이름(12자)과 달리 이유는 문장이라 중간에서 자르면 더 나쁘다.
ALTER TABLE suggestions ADD COLUMN reason TEXT;
