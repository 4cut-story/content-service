package net.java21.fourcut.content.comic;

import java.time.Instant;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 만화함 항목·만화 상세.
 *
 * @param id 만화 공개 ID
 * @param conversationId 대화 공개 ID
 * @param nameWarnings 대사에 남은 실명 의심 단어. 올리기 전에 보여 준다
 * @param postId 올린 게시물 공개 ID. 만화함에만 있으면 null
 * @param cast 등장인물 소개. 예전 만화는 빈 목록
 * @param concept 그린 그림체. 예전 만화는 null
 * @param dialect 대사 사투리. 예전 만화는 null
 * @param summary 만화 아래 줄거리 요약. 예전 만화는 null
 * @param production 제작 정보 (단계별 모델·조건·걸린 시간). 예전 만화는 null
 * @param live 라이브 모드 재생 정보가 있는지. 예전 만화는 false(만화보기만)
 */
public record ComicView(
    String id,
    String conversationId,
    String title,
    String language,
    List<String> nameWarnings,
    Comic.State state,
    String postId,
    Instant createdAt,
    List<CastView> cast,
    String concept,
    String dialect,
    String summary,
    JsonNode production,
    boolean live) {}
