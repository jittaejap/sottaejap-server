-- 카드사 구분을 버린다. 정본: sottaejap-docs/04_데이터모델_ERD.md §1 · §4 · 05 §2.
-- CSV 파싱은 카드사가 아니라 머리글 컬럼 구성으로 서식을 가른다. 어느 카드사 파일이든 같은 스키마로 적재된다.
-- card_issuer는 업로드할 때 사용자가 손으로 고르던 값이라 파일과 어긋날 수 있었고, 어디에서도 읽지 않는다.

ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_card_issuer_check;
ALTER TABLE transactions DROP COLUMN IF EXISTS card_issuer;
