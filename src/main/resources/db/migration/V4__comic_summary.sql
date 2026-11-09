-- 만화 아래에 보이는 줄거리 요약(몇 문장). 콘티를 만들 때 conversation-service가 함께 만든다.
-- 예전 만화는 conversation-service가 남은 콘티로 채우고, 못 채우면 비어 있다.
ALTER TABLE comics ADD COLUMN summary VARCHAR(1000);
