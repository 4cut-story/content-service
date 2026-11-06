package net.java21.fourcut.content.comic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import net.java21.fourcut.content.support.PublicId;

/**
 * 만화 아래에 보이는 등장인물 한 명.
 *
 * @param role 만화 속 호칭 (할머니, 손녀…)
 * @param name 캐릭터 이름
 * @param lookId 캐릭터 모습 ID (그림은 {@code /api/v1/characters/looks/{lookId}/image}). 없으면 null
 */
public record CastMember(
    @NotBlank @Size(max = 20) String role,
    @NotBlank @Size(max = 50) String name,
    @Pattern(regexp = PublicId.PATTERN) String lookId) {}
