package net.java21.fourcut.content.comment;

import java.time.Instant;

/** 댓글. 작성자 이름·사진은 web이 auth-service에서 모아 붙인다. */
public record CommentView(
    String id, String postId, String authorId, String text, Instant createdAt) {

  /** 댓글 보기. postId는 게시물의 공개 ID다. */
  static CommentView of(Comment c, String postId) {
    return new CommentView(c.getPublicId(), postId, c.getAuthorId(), c.getText(), c.getCreatedAt());
  }
}
