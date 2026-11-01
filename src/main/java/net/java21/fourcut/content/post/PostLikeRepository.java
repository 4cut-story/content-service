package net.java21.fourcut.content.post;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostLikeRepository extends JpaRepository<PostLike, Long> {

  boolean existsByPostIdAndUserId(Long postId, String userId);

  @Modifying
  @Query("delete from PostLike l where l.postId = ?1 and l.userId = ?2")
  int deleteByPostIdAndUserId(Long postId, String userId);

  @Query("select l.postId from PostLike l where l.userId = ?1 and l.postId in ?2")
  List<Long> likedPostIds(String userId, Collection<Long> postIds);

  List<PostLike> findByUserId(String userId);
}
