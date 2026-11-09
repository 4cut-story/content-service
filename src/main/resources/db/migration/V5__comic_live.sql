-- 라이브 모드 재생 정보 (docs specs/003-live-mode, common contracts/live-mode.yaml LivePlayback).
-- 컷 순서·컷 그림·보여 주는 시간·배경음·움직임을 conversation-service가 만든 그대로 둔다. 예전 만화는 null이고 만화보기만 보여 준다.
ALTER TABLE comics ADD COLUMN live JSON;
