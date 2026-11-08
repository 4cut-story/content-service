package net.java21.fourcut.content.comic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.java21.fourcut.content.support.PublicId;

/**
 * 만화 아래에 보이는 등장인물 한 명 (conversation-service가 보내고 그대로 저장하는 모양). 밖으로는 {@link CastView}로 보인다.
 *
 * @param role 만화 속 호칭 (할머니, 손녀…)
 * @param name 캐릭터 이름
 * @param lookId 캐릭터 모습 ID (그림은 {@code /api/v1/characters/looks/{lookId}/image}). 없으면 null
 * @param portraitKey 캐릭터 DB에 없는 인물의 얼굴 그림 객체 키 (만화 컷에서 잘라 낸 것). 없으면 null. 저장소 키라 밖에는 보이지 않는다
 */
public record CastMember(
    @NotBlank @Size(max = 20) String role,
    @NotBlank @Size(max = 50) String name,
    @Pattern(regexp = PublicId.PATTERN) String lookId,
    @Pattern(regexp = PORTRAIT_KEY) String portraitKey) {

  /** 얼굴 그림 키는 그 만화의 이미지 폴더 안 PNG만 받는다. */
  static final String PORTRAIT_KEY = "comics/[A-Za-z0-9_-]{1,64}/cast-[0-9]{1,2}\\.png";

  public CastMember(String role, String name, String lookId) {
    this(role, name, lookId, null);
  }
}
