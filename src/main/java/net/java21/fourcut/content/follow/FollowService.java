package net.java21.fourcut.content.follow;

import java.util.UUID;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팔로우 (승인 없이 바로, 멱등). */
@Service
public class FollowService {

  private final FollowRepository follows;

  public FollowService(FollowRepository follows) {
    this.follows = follows;
  }

  @Transactional
  public void follow(UUID follower, UUID followee) {
    if (follower.equals(followee)) {
      throw new ApiException(ErrorCode.SELF_FOLLOW);
    }
    if (!follows.existsByFollowerIdAndFolloweeId(follower, followee)) {
      follows.saveAndFlush(new Follow(follower, followee));
    }
  }

  @Transactional
  public void unfollow(UUID follower, UUID followee) {
    follows.unfollow(follower, followee);
  }

  @Transactional(readOnly = true)
  public FollowStats stats(UUID viewer, UUID user) {
    return new FollowStats(
        follows.countByFolloweeId(user),
        follows.countByFollowerId(user),
        follows.existsByFollowerIdAndFolloweeId(viewer, user));
  }
}
