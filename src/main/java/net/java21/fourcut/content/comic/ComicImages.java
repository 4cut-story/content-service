package net.java21.fourcut.content.comic;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.ImageIO;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;

/** 올린 만화 그림을 확인하고 썸네일을 만든다. 파이프라인 썸네일과 같이 폭 400px PNG다. */
final class ComicImages {

  static final int THUMB_WIDTH = 400;

  private ComicImages() {}

  /** 확인한 그림: 원본 바이트, 형식(png, jpg), 그림. */
  record Decoded(byte[] bytes, String extension, String contentType, BufferedImage image) {}

  /** Base64를 풀어 PNG·JPEG인지 확인한다. 아니면 415, Base64가 틀리면 400. */
  static Decoded decode(String base64) {
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(base64);
    } catch (IllegalArgumentException e) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.message(), e);
    }
    String extension;
    String contentType;
    if (startsWith(bytes, 0x89, 'P', 'N', 'G')) {
      extension = "png";
      contentType = "image/png";
    } else if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
      extension = "jpg";
      contentType = "image/jpeg";
    } else {
      throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
    BufferedImage image;
    try {
      image = ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      image = null;
    }
    if (image == null) {
      throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE);
    }
    return new Decoded(bytes, extension, contentType, image);
  }

  /** 폭 400px로 줄인 PNG. 원본이 더 좁으면 그대로 둔다. */
  static byte[] thumbnail(BufferedImage image) {
    int width = Math.min(THUMB_WIDTH, image.getWidth());
    int height = Math.max(1, Math.round((float) image.getHeight() * width / image.getWidth()));
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    var g = out.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(image, 0, 0, width, height, null);
    g.dispose();
    try (var buffer = new ByteArrayOutputStream()) {
      ImageIO.write(out, "png", buffer);
      return buffer.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static boolean startsWith(byte[] bytes, int... prefix) {
    if (bytes.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((bytes[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }
}
