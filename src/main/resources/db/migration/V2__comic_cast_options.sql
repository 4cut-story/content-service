-- 만화 아래에 보이는 등장인물 소개(호칭·캐릭터 이름·캐릭터 모습 ID, JSON 배열)와 만화를 만든 옵션(그림체, 대사 사투리).
-- 예전 만화는 비어 있다.
ALTER TABLE comics ADD COLUMN cast_members JSON;
ALTER TABLE comics ADD COLUMN concept VARCHAR(20);
ALTER TABLE comics ADD COLUMN dialect VARCHAR(20);
