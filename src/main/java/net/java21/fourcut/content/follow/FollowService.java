package net.java21.fourcut.content.follow;

import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.support.PublicId;
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
  public void follow(String follower, String followee) {
    requireUserId(followee);
    if (follower.equals(followee)) {
      throw new ApiException(ErrorCode.SELF_FOLLOW);
    }
    if (!follows.existsByFollowerIdAndFolloweeId(follower, followee)) {
      follows.saveAndFlush(new Follow(follower, followee));
    }
  }

  @Transactional
  public void unfollow(String follower, String followee) {
    requireUserId(followee);
    follows.unfollow(follower, followee);
  }

  @Transactional(readOnly = true)
  public FollowStats stats(String viewer, String user) {
    requireUserId(user);
    return new FollowStats(
        follows.countByFolloweeId(user),
        follows.countByFollowerId(user),
        follows.existsByFollowerIdAndFolloweeId(viewer, user));
  }

  /** 사용자 공개 ID 형식이 아니면 없는 사용자로 본다(404). */
  private static void requireUserId(String userId) {
    if (!PublicId.isValid(userId)) {
      throw ApiException.notFound();
    }
  }
}
