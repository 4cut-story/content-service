-- content 스키마 첫 버전. 원본: Crowfoot 4cut-mvp v24 (docs 레포 docs/db/4cut-mvp.sql의 content 그룹)
-- ID 규칙: 기본 키는 BIGINT IDENTITY, 밖에 보이는 리소스는 NanoID public_id(21자)를 따로 둔다.
-- 이미 v24가 반영된 DB(개발 공용 스키마)에서는 Flyway baseline(1)으로 건너뛴다.
CREATE TABLE comics (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    owner_id VARCHAR(21) NOT NULL,
    conversation_id VARCHAR(21) NOT NULL,
    title VARCHAR(100) NOT NULL,
    image_key VARCHAR(500) NOT NULL,
    thumb_key VARCHAR(500) NOT NULL,
    language VARCHAR(2) NOT NULL,
    name_warnings JSON,
    state VARCHAR(20) NOT NULL DEFAULT 'IN_INBOX',
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    public_id VARCHAR(21) NOT NULL,
    CONSTRAINT comics_pk PRIMARY KEY (id),
    CONSTRAINT uk_comics_conversation_id UNIQUE (conversation_id),
    CONSTRAINT uk_comics_public_id UNIQUE (public_id),
    CONSTRAINT ck_comics_state CHECK (state IN ('IN_INBOX', 'POSTED')),
    CONSTRAINT ck_comics_language CHECK (language IN ('ko', 'en'))
);
CREATE TABLE posts (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    comic_id BIGINT NOT NULL,
    author_id VARCHAR(21) NOT NULL,
    caption VARCHAR(100),
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC',
    participants_consented BOOLEAN NOT NULL,
    published_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    public_id VARCHAR(21) NOT NULL,
    CONSTRAINT posts_pk PRIMARY KEY (id),
    CONSTRAINT uk_posts_public_id UNIQUE (public_id),
    CONSTRAINT uk_posts_comic_id UNIQUE (comic_id),
    CONSTRAINT ck_posts_visibility CHECK (visibility IN ('PUBLIC', 'FOLLOWERS')),
    CONSTRAINT ck_posts_consented CHECK (participants_consented = true),
    CONSTRAINT ck_posts_counts CHECK (like_count >= 0 AND comment_count >= 0)
);
CREATE TABLE likes (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    post_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    user_id VARCHAR(21) NOT NULL,
    CONSTRAINT likes_pk PRIMARY KEY (id),
    CONSTRAINT uk_likes_post_id_user_id UNIQUE (post_id, user_id)
);
CREATE TABLE comments (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    post_id BIGINT NOT NULL,
    author_id VARCHAR(21) NOT NULL,
    text VARCHAR(300) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    public_id VARCHAR(21) NOT NULL,
    CONSTRAINT comments_pk PRIMARY KEY (id),
    CONSTRAINT uk_comments_public_id UNIQUE (public_id),
    CONSTRAINT ck_comments_text CHECK (char_length(text) BETWEEN 1 AND 300)
);
CREATE TABLE follows (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    follower_id VARCHAR(21) NOT NULL,
    followee_id VARCHAR(21) NOT NULL,
    created_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT follows_pk PRIMARY KEY (id),
    CONSTRAINT uk_follows_follower_id_followee_id UNIQUE (follower_id, followee_id),
    CONSTRAINT ck_follows_not_self CHECK (follower_id <> followee_id)
);
ALTER TABLE posts ADD CONSTRAINT fk_posts_comics FOREIGN KEY (comic_id) REFERENCES comics (id) ON DELETE CASCADE;
ALTER TABLE likes ADD CONSTRAINT fk_likes_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE comments ADD CONSTRAINT fk_comments_posts FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
CREATE INDEX idx_comics_owner_id_created_at ON comics (owner_id ASC, created_at DESC);
CREATE INDEX idx_posts_published_at_id ON posts (published_at DESC, id DESC);
CREATE INDEX idx_posts_author_id_published_at ON posts (author_id ASC, published_at DESC);
CREATE INDEX idx_likes_user_id ON likes (user_id ASC);
CREATE INDEX idx_likes_post_id ON likes (post_id ASC);
CREATE INDEX idx_comments_post_id_created_at ON comments (post_id ASC, created_at ASC);
CREATE INDEX idx_comments_author_id ON comments (author_id ASC);
CREATE INDEX idx_comments_post_id ON comments (post_id ASC);
CREATE INDEX idx_follows_followee_id ON follows (followee_id ASC);
