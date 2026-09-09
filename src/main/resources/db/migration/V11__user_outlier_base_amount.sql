-- 큰 금액 후보 판정(THRESHOLD_EXCEEDED)의 사용자 기준 금액, 원 단위 (01 E-115 · 05 §2 · client #33).
-- 값이 있으면 규칙 ③이 `amount >= outlier_base_amount`로 판정하고, 없으면 지금처럼
-- `monthly_budget * rules.candidate.big-amount-budget-ratio`로 돌아간다.
--
-- 이상치 배수 outlier_threshold(E-46)와 다른 값이다 — 배수는 규칙 ④ TIMESLOT_OUTLIER의 것이다.
--
-- nullable이고 백필하지 않는다. 지금 배포된 client는 이 값을 보내지 않으므로 기존 행에 채울 근거가
-- 없고, 임의의 금액을 넣으면 사용자가 정하지 않은 기준으로 후보가 뽑힌다. null이 정직하다.
ALTER TABLE users ADD COLUMN outlier_base_amount INTEGER;
