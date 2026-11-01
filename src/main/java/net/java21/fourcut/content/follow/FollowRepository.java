package net.java21.fourcut.content.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, Long> {

  boolean existsByFollowerIdAndFolloweeId(String followerId, String followeeId);

  long countByFolloweeId(String followeeId);

  long countByFollowerId(String followerId);

  @Modifying
  @Query("delete from Follow f where f.followerId = ?1 and f.followeeId = ?2")
  int unfollow(String followerId, String followeeId);

  @Modifying
  @Query("delete from Follow f where f.followerId = ?1 or f.followeeId = ?1")
  int deleteAllOf(String userId);
}
