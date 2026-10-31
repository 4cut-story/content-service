package net.java21.fourcut.content.comment;

import java.time.Instant;
import java.util.UUID;

/** 댓글. 작성자 이름·사진은 web이 auth-service에서 모아 붙인다. */
public record CommentView(UUID id, UUID postId, UUID authorId, String text, Instant createdAt) {

  static CommentView of(Comment c) {
    return new CommentView(
        c.getId(), c.getPostId(), c.getAuthorId(), c.getText(), c.getCreatedAt());
  }
}
