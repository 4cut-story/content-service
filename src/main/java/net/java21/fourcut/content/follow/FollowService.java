package net.java21.fourcut.content.follow;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.java21.fourcut.content.comic.ComicRepository;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.post.PostRepository;
import net.java21.fourcut.content.support.Cursor;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.support.PublicId;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 팔로우 (승인 없이 바로, 멱등)와 프로필 숫자. */
@Service
public class FollowService {

  private final FollowRepository follows;
  private final PostRepository posts;
  private final ComicRepository comics;

  public FollowService(FollowRepository follows, PostRepository posts, ComicRepository comics) {
    this.follows = follows;
    this.posts = posts;
    this.comics = comics;
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
        follows.existsByFollowerIdAndFolloweeId(viewer, user),
        posts.countByAuthor(viewer, user),
        posts.likesByAuthor(viewer, user),
        viewer.equals(user) ? comics.countByOwnerId(user) : null);
  }

  /** 이 사용자를 팔로우한 사람 목록 (최근 순). 누구나 볼 수 있다. */
  @Transactional(readOnly = true)
  public CursorPage<FollowEntry> followers(
      String viewer, String user, String cursor, Integer size) {
    requireUserId(user);
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return page(
        viewer,
        limit,
        follows.followersOf(user, from.at(), from.id(), Limit.of(limit + 1)),
        Follow::getFollowerId);
  }

  /** 이 사용자가 팔로우한 사람 목록 (최근 순). 누구나 볼 수 있다. */
  @Transactional(readOnly = true)
  public CursorPage<FollowEntry> following(
      String viewer, String user, String cursor, Integer size) {
    requireUserId(user);
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return page(
        viewer,
        limit,
        follows.followingOf(user, from.at(), from.id(), Limit.of(limit + 1)),
        Follow::getFolloweeId);
  }

  private CursorPage<FollowEntry> page(
      String viewer, int limit, List<Follow> rows, Function<Follow, String> other) {
    return CursorPage.of(
        rows,
        limit,
        f -> new Cursor(f.getCreatedAt(), f.getId()),
        page -> {
          List<String> ids = page.stream().map(other).toList();
          Set<String> mine =
              ids.isEmpty() ? Set.of() : new HashSet<>(follows.followedAmong(viewer, ids));
          return page.stream()
              .map(
                  f ->
                      new FollowEntry(
                          other.apply(f), f.getCreatedAt(), mine.contains(other.apply(f))))
              .toList();
        });
  }

  /** 사용자 공개 ID 형식이 아니면 없는 사용자로 본다(404). */
  private static void requireUserId(String userId) {
    if (!PublicId.isValid(userId)) {
      throw ApiException.notFound();
    }
  }
}
