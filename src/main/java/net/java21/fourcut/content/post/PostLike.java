package net.java21.fourcut.content.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import net.java21.fourcut.content.support.BaseEntity;

/** 좋아요. 한 사람당 게시물마다 하나. */
@Entity
@Table(name = "likes")
public class PostLike extends BaseEntity {

  @Column(name = "post_id", nullable = false)
  private Long postId;

  @Column(name = "user_id", nullable = false)
  private String userId;

  protected PostLike() {
    // JPA 전용
  }

  public PostLike(Long postId, String userId) {
    this.postId = postId;
    this.userId = userId;
  }

  public Long getPostId() {
    return postId;
  }

  public String getUserId() {
    return userId;
  }
}
