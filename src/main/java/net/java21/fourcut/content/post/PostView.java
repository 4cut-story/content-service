package net.java21.fourcut.content.post;

import java.time.Instant;
import java.util.UUID;

/** 피드·프로필·상세에 쓰는 게시물. 이미지는 {@code /api/v1/posts/{id}/image}로 따로 받는다. */
public record PostView(
    UUID postId,
    UUID comicId,
    UUID authorId,
    String title,
    String caption,
    Visibility visibility,
    Instant publishedAt,
    int likeCount,
    int commentCount,
    boolean likedByMe) {}
