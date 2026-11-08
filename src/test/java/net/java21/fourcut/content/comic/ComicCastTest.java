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
}
