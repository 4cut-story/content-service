package net.java21.fourcut.content.follow;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, Long> {

  boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

  long countByFolloweeId(String followeeId);

  long countByFollowerId(String followerId);

  String BEFORE = "(f.createdAt < :at or (f.createdAt = :at and f.id < :id))";

  /** 이 사용자를 팔로우한 사람 (최근 순). */
  @Query(
      "select f from Follow f where f.followeeId = :user and "
          + BEFORE
          + " order by f.createdAt desc, f.id desc")
  List<Follow> followersOf(String user, Instant at, long id, Limit limit);

  /** 이 사용자가 팔로우한 사람 (최근 순). */
  @Query(
      "select f from Follow f where f.followerId = :user and "
          + BEFORE
          + " order by f.createdAt desc, f.id desc")
  List<Follow> followingOf(String user, Instant at, long id, Limit limit);

  /** users 가운데 viewer가 팔로우하는 사람. */
  @Query(
      "select f.followeeId from Follow f where f.followerId = :viewer and f.followeeId in :users")
  List<String> followedAmong(String viewer, Collection<String> users);

  @Modifying
  @Query("delete from Follow f where f.followerId = ?1 and f.followeeId = ?2")
  int unfollow(String followerId, String followeeId);

  @Modifying
  @Query("delete from Follow f where f.followerId = ?1 or f.followeeId = ?1")
  int deleteAllOf(String userId);
}
