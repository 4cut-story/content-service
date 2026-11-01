package net.java21.fourcut.content.web;

/** 보는 사람. 로그인하지 않은 요청에는 게이트웨이가 {@code X-User-Id}를 붙이지 않는다. */
public final class Viewer {

  /** 로그인하지 않은 사람. 어떤 사용자 ID와도 같지 않아서 전체 공개 게시물만 보인다. */
  public static final String ANONYMOUS = "";

  private Viewer() {}

  public static String of(String userIdHeader) {
    return userIdHeader == null || userIdHeader.isBlank() ? ANONYMOUS : userIdHeader;
  }
}
