package net.java21.fourcut.content.comic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * 이미 그린 만화 그림으로 만화함에 만화를 넣는 본문. 모델 비교처럼 파이프라인 밖에서 그린 시험 만화를 올릴 때 쓴다.
 *
 * @param image 완성 만화 그림 (PNG 또는 JPEG, Base64). 썸네일은 서버가 만든다
 * @param language 대사 언어. 없으면 ko
 * @param production 제작 정보 (JSON 객체, conversation-service 제작 정보 version 1과 같은 형식)
 */
public record ImportComicRequest(
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = ImportComicRequest.MAX_IMAGE_BASE64) String image,
    @Pattern(regexp = "ko|en") String language,
    @Valid @Size(max = 10) List<CastMember> cast,
    @Pattern(regexp = RegisterComicRequest.OPTION) String concept,
    @Pattern(regexp = RegisterComicRequest.OPTION) String dialect,
    JsonNode production) {

  /** Base64 그림 최대 길이 (원본 약 9MB). 컷 10개 세로 그림도 PNG로 들어간다. */
  static final int MAX_IMAGE_BASE64 = 12_000_000;
}
