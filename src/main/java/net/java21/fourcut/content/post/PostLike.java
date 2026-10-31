package net.java21.fourcut.content.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import net.java21.fourcut.content.support.BaseEntity;

/** 좋아요. 한 사람당 게시물마다 하나. */
@Entity
@Table(name = "likes")
public class PostLike extends BaseEntity {

  @Column(name = "post_id", nullable = false)
  private UUID postId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  protected PostLike() {
    // JPA 전용
  }

  public PostLike(UUID postId, UUID userId) {
    this.postId = postId;
    this.userId = userId;
  }

  public UUID getPostId() {
    return postId;
  }

  public UUID getUserId() {
    return userId;
  }
}
