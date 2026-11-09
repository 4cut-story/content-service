package net.java21.fourcut.content.post;

import java.time.Instant;
import java.util.List;
import net.java21.fourcut.content.comic.CastView;
import tools.jackson.databind.JsonNode;

/**
 * 피드·프로필·상세에 쓰는 게시물. ID는 모두 공개 ID다. 이미지는 {@code /api/v1/posts/{id}/image}로 따로 받는다.
 *
 * @param cast 만화 아래 등장인물 소개. 예전 만화는 빈 목록
 * @param concept 그린 그림체. 예전 만화는 null
 * @param dialect 대사 사투리. 예전 만화는 null
 * @param summary 만화 아래 줄거리 요약. 예전 만화는 null
 * @param production 제작 정보 (단계별 모델·조건·걸린 시간). 예전 만화는 null
 * @param live 라이브 모드 재생 정보가 있는지. 예전 만화는 false(만화보기만)
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
    List<CastView> cast,
    String concept,
    String dialect,
    String summary,
    JsonNode production,
    boolean live) {}
