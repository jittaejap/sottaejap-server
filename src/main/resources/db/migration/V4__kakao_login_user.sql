-- 카카오 로그인 사용자 (E-56 · 06 R15). 정본: docs/04_데이터모델_ERD.md v2.1 §1 User.
-- 카카오 이메일은 선택 동의 항목이라 비어서 올 수 있다. 표시 이름은 nickname이 맡는다.
-- 소셜 식별자 (auth_provider, provider_user_id) 부분 유일 인덱스 uq_users_provider는 V1에 이미 있다.

ALTER TABLE users ALTER COLUMN email DROP NOT NULL;

-- 이메일은 식별자가 아니다. 데모 계정과 같은 이메일의 카카오 계정도 만들어져야 한다 — 이메일로 계정을 합치지도, 막지도 않는다.
ALTER TABLE users DROP CONSTRAINT uq_users_email;

-- 마이페이지 1. 프로필 표시 이름. 카카오 kakao_account.profile.nickname 저장 (05 §2 실측 ①).
ALTER TABLE users ADD COLUMN nickname VARCHAR(100);

UPDATE users
SET nickname = '데모 사용자'
WHERE auth_provider = 'LOCAL' AND email = 'demo@sottaejap.kr';
