-- 목록 썸네일: 4컷 그림의 첫 컷만 잘라 폭 480px JPEG로 줄인 그림의 객체 키.
-- 예전 만화는 null이고, 처음 요청하거나 주기 작업(ComicCovers)이 만들어 채운다.
ALTER TABLE comics ADD COLUMN cover_key VARCHAR(500);
