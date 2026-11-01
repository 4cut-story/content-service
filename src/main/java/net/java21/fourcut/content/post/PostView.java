package net.java21.fourcut.content.post;

import java.time.Instant;

/** 피드·프로필·상세에 쓰는 게시물. ID는 모두 공개 ID다. 이미지는 {@code /api/v1/posts/{id}/image}로 따로 받는다. */
public record PostView(
    String postId,
    String comicId,
    String authorId,
    String title,
    String caption,
    Visibility visibility,
    Instant publishedAt,
    int likeCount,
    int commentCount,
    boolean likedByMe) {}
