package net.java21.fourcut.content.comic;

import java.time.Instant;
import java.util.List;

/**
 * 만화함 항목·만화 상세.
 *
 * @param id 만화 공개 ID
 * @param conversationId 대화 공개 ID
 * @param nameWarnings 대사에 남은 실명 의심 단어. 올리기 전에 보여 준다
 * @param postId 올린 게시물 공개 ID. 만화함에만 있으면 null
 */
public record ComicView(
    String id,
    String conversationId,
    String title,
    String language,
    List<String> nameWarnings,
    Comic.State state,
    String postId,
    Instant createdAt) {}
