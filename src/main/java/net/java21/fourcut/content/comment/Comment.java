package net.java21.fourcut.content.comment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import net.java21.fourcut.content.support.BaseEntity;
import net.java21.fourcut.content.support.PublicId;

/** 댓글. 답글 없이 한 단계. */
@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {

  @Column(name = "post_id", nullable = false)
  private Long postId;

  /** 밖에 보이는 댓글 ID. */
  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  @Column(name = "author_id", nullable = false)
  private String authorId;

  @Column(nullable = false, length = 300)
  private String text;

  protected Comment() {
    // JPA 전용
  }

  public Comment(Long postId, String authorId, String text) {
    this.postId = postId;
    this.authorId = authorId;
    this.text = text;
  }

  public Long getPostId() {
    return postId;
  }

  public String getPublicId() {
    return publicId;
  }

  public String getAuthorId() {
    return authorId;
  }

  public String getText() {
    return text;
  }
}
