package net.java21.fourcut.content.comic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComicImagesTest {

  private static String encode(int width, int height, String format) throws Exception {
    var out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
    return Base64.getEncoder().encodeToString(out.toByteArray());
  }

  private static ErrorCode codeOf(String base64) {
    try {
      ComicImages.decode(base64);
    } catch (ApiException e) {
      return e.code();
    }
    return null;
  }

  @Test
  @DisplayName("PNG와 JPEG를 알아보고 형식에 맞는 확장자를 준다")
  void decode() throws Exception {
    assertThat(ComicImages.decode(encode(8, 8, "png")).extension()).isEqualTo("png");
    var jpeg = ComicImages.decode(encode(8, 8, "jpg"));
    assertThat(jpeg.extension()).isEqualTo("jpg");
    assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
  }

  @Test
  @DisplayName("Base64가 틀리면 400, PNG·JPEG가 아니거나 깨졌으면 415")
  void rejects() {
    assertThat(codeOf("@@@")).isEqualTo(ErrorCode.VALIDATION_FAILED);
    assertThat(codeOf("R0lGODlhAQABAAAAACw=")).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    assertThat(codeOf("iVBORw0KGgo=")).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    assertThat(codeOf("/9j/")).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    assertThat(codeOf("")).isEqualTo(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    assertThatThrownBy(() -> ComicImages.decode("iV")).isInstanceOf(ApiException.class);
  }

  @Test
  @DisplayName("썸네일은 폭 400px로 줄이고, 더 좁은 그림은 그대로 둔다")
  void thumbnail() throws Exception {
    var big =
        ImageIO.read(
            new ByteArrayInputStream(ComicImages.thumbnail(new BufferedImage(800, 2000, 1))));
    assertThat(big.getWidth()).isEqualTo(400);
    assertThat(big.getHeight()).isEqualTo(1000);
    var small =
        ImageIO.read(
            new ByteArrayInputStream(ComicImages.thumbnail(new BufferedImage(200, 50, 1))));
    assertThat(small.getWidth()).isEqualTo(200);
  }
}
