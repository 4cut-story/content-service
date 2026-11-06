package net.java21.fourcut.content.post;

import java.time.Instant;
import java.util.List;
import net.java21.fourcut.content.comic.CastMember;

/**
 * 피드·프로필·상세에 쓰는 게시물. ID는 모두 공개 ID다. 이미지는 {@code /api/v1/posts/{id}/image}로 따로 받는다.
 *
 * @param cast 만화 아래 등장인물 소개. 예전 만화는 빈 목록
 * @param concept 그린 그림체. 예전 만화는 null
 * @param dialect 대사 사투리. 예전 만화는 null
 */
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
    boolean likedByMe,
    List<CastMember> cast,
    String concept,
    String dialect) {}
