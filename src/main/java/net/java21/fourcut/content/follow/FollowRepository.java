package net.java21.fourcut.content.follow;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFollowerIdAndFolloweeId(UUID followerId, UUID followeeId);

  long countByFolloweeId(UUID followeeId);

  long countByFollowerId(UUID followerId);

  @Modifying
  @Query("delete from Follow f where f.followerId = ?1 and f.followeeId = ?2")
  int unfollow(UUID followerId, UUID followeeId);

  @Modifying
  @Query("delete from Follow f where f.followerId = ?1 or f.followeeId = ?1")
  int deleteAllOf(UUID userId);
}
