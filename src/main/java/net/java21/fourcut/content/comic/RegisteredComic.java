package net.java21.fourcut.content.comic;

/**
 * 등록 결과.
 *
 * @param comicId 만화 공개 ID
 * @param created 새로 만들었으면 true, 같은 대화로 이미 있었으면 false
 */
public record RegisteredComic(String comicId, boolean created) {}
