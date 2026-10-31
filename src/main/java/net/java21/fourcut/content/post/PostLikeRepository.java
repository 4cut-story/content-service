package net.java21.fourcut.content.post;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

  boolean existsByPostIdAndUserId(UUID postId, UUID userId);

  @Modifying
  @Query("delete from PostLike l where l.postId = ?1 and l.userId = ?2")
  int deleteByPostIdAndUserId(UUID postId, UUID userId);

  @Query("select l.postId from PostLike l where l.userId = ?1 and l.postId in ?2")
  List<UUID> likedPostIds(UUID userId, Collection<UUID> postIds);

  List<PostLike> findByUserId(UUID userId);
}
