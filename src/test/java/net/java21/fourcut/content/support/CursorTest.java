package net.java21.fourcut.content.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import net.java21.fourcut.content.error.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CursorTest {

  @Test
  @DisplayName("커서는 인코딩한 값으로 다시 풀린다")
  void roundTrip() {
    Cursor cursor = new Cursor(Instant.parse("2026-10-31T01:02:03.123456Z"), 1234567890123L);
    assertThat(Cursor.decodeOr(cursor.encode(), Cursor.NEWEST_FIRST)).isEqualTo(cursor);
  }

  @Test
  @DisplayName("커서가 없으면 첫 페이지 기준을 준다")
  void empty() {
    assertThat(Cursor.decodeOr(null, Cursor.OLDEST_FIRST)).isEqualTo(Cursor.OLDEST_FIRST);
    assertThat(Cursor.decodeOr(" ", Cursor.NEWEST_FIRST)).isEqualTo(Cursor.NEWEST_FIRST);
  }

  @Test
  @DisplayName("형식이 틀린 커서는 400")
  void invalid() {
    String wrongJson =
        Base64.getUrlEncoder().encodeToString("{\"x\":1}".getBytes(StandardCharsets.UTF_8));
    String badTime =
        Base64.getUrlEncoder()
            .encodeToString("{\"t\":\"yesterday\",\"id\":1}".getBytes(StandardCharsets.UTF_8));
    String uuidId =
        Base64.getUrlEncoder()
            .encodeToString(
                "{\"t\":\"2026-10-31T00:00:00Z\",\"id\":\"0193a1c2-7e5b-7c3d-9a2f-5b8e4d1c6a70\"}"
                    .getBytes(StandardCharsets.UTF_8));
    String tooBig =
        Base64.getUrlEncoder()
            .encodeToString(
                "{\"t\":\"2026-10-31T00:00:00Z\",\"id\":9999999999999999999}"
                    .getBytes(StandardCharsets.UTF_8));
    for (String value : List.of("%%%", wrongJson, badTime, uuidId, tooBig)) {
      assertThatThrownBy(() -> Cursor.decodeOr(value, Cursor.NEWEST_FIRST))
          .isInstanceOf(ApiException.class);
    }
  }

  @Test
  @DisplayName("size+1개를 읽었을 때만 다음 커서가 생긴다")
  void page() {
    List<Integer> rows = List.of(1, 2, 3);
    CursorPage<String> more =
        CursorPage.of(
            rows,
            2,
            i -> new Cursor(Instant.EPOCH, i),
            l -> l.stream().map(String::valueOf).toList());
    assertThat(more.items()).containsExactly("1", "2");
    assertThat(more.nextCursor()).isNotNull();
    CursorPage<String> last =
        CursorPage.of(
            rows,
            3,
            i -> new Cursor(Instant.EPOCH, i),
            l -> l.stream().map(String::valueOf).toList());
    assertThat(last.nextCursor()).isNull();
    assertThat(CursorPage.size(null)).isEqualTo(20);
    assertThatThrownBy(() -> CursorPage.size(0)).isInstanceOf(ApiException.class);
  }
}
