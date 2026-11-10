package net.java21.fourcut.content.comic;

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;

/** 올린 만화 그림을 확인하고 썸네일을 만든다. 파이프라인 썸네일과 같이 폭 400px PNG다. */
final class ComicImages {

  static final int THUMB_WIDTH = 400;

  /** 목록 썸네일(첫 컷) 폭. 목록 칸이 가장 넓을 때(약 360px)도 2배 화면에서 흐리지 않게 둔다. */
  static final int COVER_WIDTH = 480;

  /** 목록 썸네일 JPEG 품질. 0.8 안팎이면 컷 그림에서 차이가 거의 보이지 않는다. */
  static final float COVER_QUALITY = 0.8f;

  /** 합성 규격(가로 800 기준): 제목 띠 0~112 아래 첫 컷이 112~869에 있다(conversation-service). */
  private static final double FIRST_PANEL_TOP = 112.0 / 800;

  private static final double FIRST_PANEL_HEIGHT = 757.0 / 800;

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

  /**
   * 목록 썸네일: 4컷 그림에서 첫 컷만 잘라 폭 480px JPEG로 줄인다. 4컷 전체를 줄인 PNG(수백 KB~2MB)보다 수십 배 작다. 그림이 합성 규격보다 짧으면
   * 위쪽 정사각형을 쓴다.
   */
  static byte[] cover(BufferedImage image) {
    int w = image.getWidth();
    int top = (int) Math.round(w * FIRST_PANEL_TOP);
    int height = (int) Math.round(w * FIRST_PANEL_HEIGHT);
    if (top + height > image.getHeight()) {
      top = 0;
      height = Math.min(w, image.getHeight());
    }
    int width = Math.min(COVER_WIDTH, w);
    int outHeight = Math.max(1, Math.round((float) height * width / w));
    BufferedImage out = new BufferedImage(width, outHeight, BufferedImage.TYPE_INT_RGB);
    var g = out.createGraphics();
    g.setRenderingHint(
        RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(image, 0, 0, width, outHeight, 0, top, w, top + height, null);
    g.dispose();
    return jpeg(out);
  }

  private static byte[] jpeg(BufferedImage image) {
    var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
    try (var buffer = new ByteArrayOutputStream();
        var stream = new MemoryCacheImageOutputStream(buffer)) {
      ImageWriteParam param = writer.getDefaultWriteParam();
      param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
      param.setCompressionQuality(COVER_QUALITY);
      writer.setOutput(stream);
      writer.write(null, new IIOImage(image, null, null), param);
      stream.flush();
      return buffer.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } finally {
      writer.dispose();
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
