package net.java21.fourcut.content.comment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 댓글 쓰기 (1~300자). */
public record CommentRequest(@NotBlank @Size(max = 300) String text) {}
