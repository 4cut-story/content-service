package net.java21.fourcut.content.follow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import net.java21.fourcut.content.support.BaseEntity;

/** 팔로우 (승인 없이 바로). follower가 followee를 팔로우한다. */
@Entity
@Table(name = "follows")
public class Follow extends BaseEntity {

  @Column(name = "follower_id", nullable = false)
  private UUID followerId;

  @Column(name = "followee_id", nullable = false)
  private UUID followeeId;

  protected Follow() {
    // JPA 전용
  }

  public Follow(UUID followerId, UUID followeeId) {
    this.followerId = followerId;
    this.followeeId = followeeId;
  }

  public UUID getFollowerId() {
    return followerId;
  }

  public UUID getFolloweeId() {
    return followeeId;
  }
}
