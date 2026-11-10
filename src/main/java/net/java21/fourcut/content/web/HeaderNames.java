package net.java21.fourcut.content.web;

/** 서비스가 읽는 헤더 이름 (docs api-conventions 7절). 대소문자는 구분하지 않는다. */
public final class HeaderNames {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  public static final String TRACE_ID = "X-Trace-Id";
  public static final String WORKER_ID = "X-Worker-Id";

  /** 조회수 중복 판단에 쓰는 방문자 키. web이 방문자 쿠키를 SHA-256으로 바꿔 붙인다. */
  public static final String VIEWER_KEY = "X-Viewer-Key";

  private HeaderNames() {}
}
