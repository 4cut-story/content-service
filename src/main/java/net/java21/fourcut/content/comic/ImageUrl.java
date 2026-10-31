package net.java21.fourcut.content.comic;

/**
 * 짧게 쓰는 사전 서명 이미지 주소. web 서버만 받아서 이미지를 중계하고, 브라우저에는 넘기지 않는다.
 *
 * @param expiresIn 유효 시간(초)
 */
public record ImageUrl(String url, long expiresIn) {}
