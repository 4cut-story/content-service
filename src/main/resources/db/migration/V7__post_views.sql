-- 만화별 조회수. posts.view_count에 모아 두고, 같은 사람(web이 붙인 방문자 쿠키)이 10분 안에 다시 본 것은 세지 않는다.
-- post_views는 게시물·방문자마다 마지막으로 센 시각 한 줄만 둔다. 10분이 지난 줄은 주기 작업(PostViews.sweep)이 지운다.
-- 방문자 키는 쿠키 값 그대로가 아니라 web이 SHA-256으로 바꾼 값이다.
ALTER TABLE posts ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE posts ADD CONSTRAINT ck_posts_view_count CHECK (view_count >= 0);
CREATE TABLE post_views (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    post_id BIGINT NOT NULL,
    viewer_key VARCHAR(64) NOT NULL,
    viewed_at TIMESTAMPTZ(6) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT post_views_pk PRIMARY KEY (id),
    CONSTRAINT uk_post_views_post_id_viewer_key UNIQUE (post_id, viewer_key)
);
ALTER TABLE post_views ADD CONSTRAINT fk_post_views_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
CREATE INDEX idx_post_views_viewed_at ON post_views (viewed_at ASC);
