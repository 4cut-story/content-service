package net.java21.fourcut.content.comic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import net.java21.fourcut.content.support.PublicId;
import tools.jackson.databind.JsonNode;

/**
 * conversation-service가 완성된 만화를 등록할 때 보내는 본문 (내부 API).
 *
 * @param cast 만화 아래 등장인물 소개. 예전 conversation-service는 보내지 않는다
 * @param concept 그린 그림체 (DAILY, WATERCOLOR…)
 * @param dialect 대사 사투리 (NONE, GYEONGSANG…)
 * @param summary 만화 아래 줄거리 요약 (몇 문장). 예전 conversation-service는 보내지 않는다
 * @param production 제작 정보 (JSON 객체). content-service는 내용을 보지 않고 그대로 둔다
 */
public record RegisterComicRequest(
    @NotNull @Pattern(regexp = PublicId.PATTERN) String ownerId,
    @NotNull @Pattern(regexp = PublicId.PATTERN) String conversationId,
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 500) String imageKey,
    @NotBlank @Size(max = 500) String thumbKey,
    @NotNull @Pattern(regexp = "ko|en") String language,
    List<String> nameWarnings,
    @Valid @Size(max = 10) List<CastMember> cast,
    @Pattern(regexp = OPTION) String concept,
    @Pattern(regexp = OPTION) String dialect,
    @Size(max = Comic.MAX_SUMMARY) String summary,
    JsonNode production) {

  /** 그림체·사투리 옵션 값 (conversation-service의 enum 이름). */
  static final String OPTION = "[A-Z_]{1,20}";
}
