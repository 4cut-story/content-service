package net.java21.fourcut.content.comic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ComicCastTest {

  private final ComicService service =
      new ComicService(
          null,
          null,
          null,
          JsonMapper.builder().build(),
          new ImportProperties(java.util.List.of()));

  private static Comic comic() {
    return new Comic("o", "c", "t", "i", "th", "ko", "[]");
  }

  @Test
  @DisplayName("REQ-018: 등장인물 소개와 그림체·사투리를 남기고, 예전 만화는 빈 소개를 돌려준다")
  void cast() {
    Comic old = comic();
    assertThat(service.castOf(old)).isEmpty();
    assertThat(old.getConcept()).isNull();

    Comic c = comic();
    c.describe("[{\"role\":\"할머니\",\"name\":\"하늘\",\"lookId\":null}]", "DAILY", "NONE");
    assertThat(service.castOf(c)).isEqualTo(List.of(new CastMember("할머니", "하늘", null)));
    assertThat(c.getConcept()).isEqualTo("DAILY");
    assertThat(c.getDialect()).isEqualTo("NONE");
  }

  @Test
  @DisplayName("FR-006: 제작 정보는 남긴 그대로 돌려주고, 예전 만화는 null")
  void production() {
    assertThat(service.productionOf(comic())).isNull();
    Comic c = comic();
    c.recordProduction("{\"version\":1,\"totalMs\":5}");
    assertThat(service.productionOf(c).path("totalMs").asInt()).isEqualTo(5);
  }

  @Test
  @DisplayName("REQ-018: 밖으로는 얼굴 그림 키 대신 그림이 있는지만 보이고, 캐릭터 그림이 있으면 얼굴 그림은 쓰지 않는다")
  void views() {
    Comic c = comic();
    c.describe(
        "[{\"role\":\"할머니\",\"name\":\"하늘\",\"lookId\":null,\"portraitKey\":\"comics/c/cast-0.png\"},"
            + "{\"role\":\"손녀\",\"name\":\"도윤\",\"lookId\":\"aaaaaaaaaaaaaaaaaaaaa\",\"portraitKey\":\"comics/c/cast-1.png\"},"
            + "{\"role\":\"친구\",\"name\":\"시온\",\"lookId\":null}]",
        "DAILY",
        "NONE");
    assertThat(service.castViewsOf(c))
        .containsExactly(
            new CastView("할머니", "하늘", null, true),
            new CastView("손녀", "도윤", "aaaaaaaaaaaaaaaaaaaaa", false),
            new CastView("친구", "시온", null, false));
    assertThat(service.imageKeys(c))
        .containsExactly("i", "th", "comics/c/cast-0.png", "comics/c/cast-1.png");
  }
}
