package net.java21.fourcut.content.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.java21.fourcut.content.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LivePlaybackTest {

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  @DisplayName("003 FR-018: 그림 경로가 규칙에 맞는 재생 정보만 저장하고, 그림 목록을 뽑는다")
  void validated() {
    var live =
        json.readTree(
            "{\"version\":1,\"panels\":[{\"image\":\"panels/1.png\",\"motion\":{\"frames\":[\"panels/1.png\",\"panels/1-b.png\"]}},{\"image\":\"panels/10.png\",\"motion\":null}]}");
    assertThat(LivePlayback.validated(json, live)).isNotNull();
    assertThat(LivePlayback.images(live))
        .containsExactly("panels/1.png", "panels/1-b.png", "panels/10.png");

    assertThat(LivePlayback.validated(json, null)).isNull();
    assertThat(LivePlayback.validated(json, json.readTree("[]"))).isNull();
    assertThat(LivePlayback.validated(json, json.readTree("{\"panels\":[]}"))).isNull();
    assertThat(
            LivePlayback.validated(
                json, json.readTree("{\"panels\":[{\"image\":\"panels/11.png\"}]}")))
        .isNull();
    assertThat(
            LivePlayback.validated(
                json,
                json.readTree(
                    "{\"panels\":[{\"image\":\"panels/1.png\",\"motion\":{\"frames\":[\"x.png\"]}}]}")))
        .isNull();
    String big =
        "{\"panels\":[{\"image\":\"panels/1.png\",\"pad\":\"" + "x".repeat(40_000) + "\"}]}";
    assertThat(LivePlayback.validated(json, json.readTree(big))).isNull();
  }

  @Test
  @DisplayName("003 FR-019: 컷 그림 키는 만화 그림 폴더 기준이고 frame은 a·b만 받는다")
  void paths() {
    assertThat(LivePlayback.folder("comics/abc/full.png")).isEqualTo("comics/abc/");
    assertThat(LivePlayback.folder("full.png")).isEmpty();
    assertThat(LivePlayback.path(3, true)).isEqualTo("panels/3-b.png");
    assertThat(LivePlayback.second(null)).isFalse();
    assertThat(LivePlayback.second(" A ")).isFalse();
    assertThat(LivePlayback.second("b")).isTrue();
    assertThatThrownBy(() -> LivePlayback.second("c")).isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> LivePlayback.path(0, false)).isInstanceOf(ApiException.class);
  }
}
