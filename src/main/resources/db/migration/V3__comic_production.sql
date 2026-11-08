-- 만화를 만든 과정 기록(제작 정보, JSON 객체): 단계별 모델·조건·걸린 시간, 그림 모델·양자화·step·해상도·시드, GPU.
-- conversation-service가 만든 그대로 둔다. 예전 만화는 conversation-service가 남은 단계 기록으로 채우고, 못 채우면 비어 있다.
ALTER TABLE comics ADD COLUMN production JSON;
