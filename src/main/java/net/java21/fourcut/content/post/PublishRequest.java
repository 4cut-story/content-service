package net.java21.fourcut.content.post;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 만화 올리기.
 *
 * @param participantsConsented 함께 대화한 사람의 동의를 받았는지. true가 아니면 400 CONSENT_REQUIRED
 */
public record PublishRequest(
    @NotNull String comicId,
    @Size(max = 100) String caption,
    @NotNull Visibility visibility,
    Boolean participantsConsented) {}
