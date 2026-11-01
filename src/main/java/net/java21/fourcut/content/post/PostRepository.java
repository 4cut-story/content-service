package net.java21.fourcut.content.post;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PostRepository extends JpaRepository<Post, Long> {

  /** 보는 사람이 볼 수 있는 조건: 전체 공개, 내 게시물, 내가 팔로우한 사람의 팔로워만 게시물. */
  String VISIBLE =
      "(p.visibility = net.java21.fourcut.content.post.Visibility.PUBLIC or p.authorId = :viewer"
          + " or exists (select 1 from Follow f where f.followerId = :viewer and f.followeeId = p.authorId))";

  String BEFORE = "(p.publishedAt < :at or (p.publishedAt = :at and p.id < :id))";

  Optional<Post> findByComicId(Long comicId);

  Optional<Post> findByPublicId(String publicId);

  List<Post> findByComicIdIn(Collection<Long> comicIds);

  @Query(
      "select p from Post p where "
          + VISIBLE
          + " and "
          + BEFORE
          + " order by p.publishedAt desc, p.id desc")
  List<Post> feed(String viewer, Instant at, long id, Limit limit);

  @Query(
      "select p from Post p where p.authorId = :author and "
          + VISIBLE
          + " and "
          + BEFORE
          + " order by p.publishedAt desc, p.id desc")
  List<Post> byAuthor(String viewer, String author, Instant at, long id, Limit limit);

  @Query("select count(p) > 0 from Post p where p.id = :postId and " + VISIBLE)
  boolean visibleTo(Long postId, String viewer);

  @Modifying
  @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :postId")
  int addLikes(Long postId, int delta);

  @Modifying
  @Query("update Post p set p.commentCount = p.commentCount + :delta where p.id = :postId")
  int addComments(Long postId, int delta);
}
