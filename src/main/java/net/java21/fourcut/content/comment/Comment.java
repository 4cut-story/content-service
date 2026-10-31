package net.java21.fourcut.content.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import net.java21.fourcut.content.support.BaseEntity;

/** 댓글. 답글 없이 한 단계. */
@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {

  @Column(name = "post_id", nullable = false)
  private UUID postId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(nullable = false, length = 300)
  private String text;

  protected Comment() {
    // JPA 전용
  }

  public Comment(UUID postId, UUID authorId, String text) {
    this.postId = postId;
    this.authorId = authorId;
    this.text = text;
  }

  public UUID getPostId() {
    return postId;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getText() {
    return text;
  }
}
