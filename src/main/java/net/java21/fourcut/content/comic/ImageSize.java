package net.java21.fourcut.content.comic;

import java.util.Locale;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;

/** 이미지 크기: 원본(full) 또는 썸네일(thumb, 목록에 쓰는 첫 컷 그림). */
public enum ImageSize {
  FULL,
  THUMB;

  /** 쿼리 값 해석. 없으면 원본. */
  public static ImageSize parse(String value) {
    if (value == null || value.isBlank()) {
      return FULL;
    }
    try {
      return valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "size는 full 또는 thumb이어야 해요.", e);
    }
  }
}
