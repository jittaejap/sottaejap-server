-- 목표 달성 예정일 (05 §2 #4 · #5 · client PR #28 후속). 온보딩 1단계가 고른 날짜를 담을 칸이다.
--
-- nullable이고 백필하지 않는다. 지금 배포된 client는 이 값을 보내지 않으므로 기존 행에 채울 근거가
-- 없고, 임의의 날짜를 넣으면 사용자가 정하지 않은 예정일이 화면에 뜬다. null이 정직하다.
ALTER TABLE goals ADD COLUMN target_date DATE;
