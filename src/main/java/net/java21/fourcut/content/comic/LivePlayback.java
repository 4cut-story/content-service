package net.java21.fourcut.content.comic;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 라이브 모드 재생 정보 (docs specs/003-live-mode, common contracts/live-mode.yaml LivePlayback).
 * conversation-service가 만든 JSON을 그대로 두고, 그림 경로만 확인한다. 그림은 만화 그림과 같은
 * 폴더(comics/&lt;conversationId&gt;/)의 상대 경로다.
 */
final class LivePlayback {

  /** 저장할 수 있는 최대 크기 (JSON 문자 수). 10컷이어도 몇 KB다. */
  static final int MAX_CHARS = 32_000;

  /** conversation-service가 같은 폴더에 남기는 재생 정보 파일. 만화를 지울 때 함께 지운다. */
  static final String FILE = "live.json";

  private static final Pattern IMAGE = Pattern.compile("panels/([1-9]|10)(-b)?\\.png");

  private LivePlayback() {}

  /** 저장할 JSON. 객체가 아니거나, 너무 크거나, 컷이 없거나, 그림 경로가 규칙에 맞지 않으면 null(라이브 없이 등록한다). */
  static String validated(JsonMapper json, JsonNode live) {
    if (live == null || !live.isObject() || !live.path("panels").isArray()) {
      return null;
    }
    if (live.path("panels").isEmpty() || live.path("panels").size() > 10) {
      return null;
    }
    for (JsonNode p : live.path("panels")) {
      if (!valid(p.path("image"))) {
        return null;
      }
      JsonNode motion = p.path("motion");
      if (motion.isObject()) {
        for (JsonNode f : motion.path("frames")) {
          if (!valid(f)) {
            return null;
          }
        }
      }
    }
    String raw = json.writeValueAsString(live);
    return raw.length() > MAX_CHARS ? null : raw;
  }

  private static boolean valid(JsonNode path) {
    return path.isString() && IMAGE.matcher(path.asString()).matches();
  }

  /** 재생 정보에 나오는 그림 경로(첫 그림·두 번째 그림). */
  static Set<String> images(JsonNode live) {
    Set<String> out = new LinkedHashSet<>();
    for (JsonNode p : live.path("panels")) {
      if (valid(p.path("image"))) {
        out.add(p.path("image").asString());
      }
      for (JsonNode f : p.path("motion").path("frames")) {
        if (valid(f)) {
          out.add(f.asString());
        }
      }
    }
    return out;
  }

  /** 만화 그림 키의 폴더 (끝에 /). */
  static String folder(String imageKey) {
    int slash = imageKey.lastIndexOf('/');
    return slash < 0 ? "" : imageKey.substring(0, slash + 1);
  }

  /** 컷 그림 상대 경로. */
  static String path(int panel, boolean second) {
    if (panel < 1 || panel > 10) {
      throw ApiException.notFound();
    }
    return "panels/" + panel + (second ? "-b" : "") + ".png";
  }

  /** frame 쿼리: 없거나 a면 첫 그림, b면 두 번째 그림. 그 밖은 400. */
  static boolean second(String frame) {
    if (frame == null || frame.isBlank()) {
      return false;
    }
    return switch (frame.strip().toLowerCase(Locale.ROOT)) {
      case "a" -> false;
      case "b" -> true;
      default -> throw new ApiException(ErrorCode.VALIDATION_FAILED, "frame은 a 또는 b여야 해요.");
    };
  }
}
