package net.java21.fourcut.content.comic;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** conversation-service가 완성된 만화를 등록할 때 보내는 본문 (내부 API). */
public record RegisterComicRequest(
    @NotNull UUID ownerId,
    @NotNull UUID conversationId,
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 500) String imageKey,
    @NotBlank @Size(max = 500) String thumbKey,
    @NotNull @Pattern(regexp = "ko|en") String language,
    List<String> nameWarnings) {}
