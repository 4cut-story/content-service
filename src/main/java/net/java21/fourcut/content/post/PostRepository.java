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

  /** 내가 팔로우한 사람의 게시물만 (전체 공개와 팔로워만 모두). */
  @Query(
      "select p from Post p where exists (select 1 from Follow f where f.followerId = :viewer"
          + " and f.followeeId = p.authorId) and "
          + BEFORE
          + " order by p.publishedAt desc, p.id desc")
  List<Post> followingFeed(String viewer, Instant at, long id, Limit limit);

  @Query(
      "select p from Post p where p.authorId = :author and "
          + VISIBLE
          + " and "
          + BEFORE
          + " order by p.publishedAt desc, p.id desc")
  List<Post> byAuthor(String viewer, String author, Instant at, long id, Limit limit);

  @Query("select count(p) from Post p where p.authorId = :author and " + VISIBLE)
  long countByAuthor(String viewer, String author);

  @Query(
      "select coalesce(sum(p.likeCount), 0) from Post p where p.authorId = :author and " + VISIBLE)
  long likesByAuthor(String viewer, String author);

  @Query("select count(p) > 0 from Post p where p.id = :postId and " + VISIBLE)
  boolean visibleTo(Long postId, String viewer);

  @Modifying
  @Query("update Post p set p.likeCount = p.likeCount + :delta where p.id = :postId")
  int addLikes(Long postId, int delta);

  /**
   * 방문자가 이 게시물을 본 시각을 남긴다. 처음 보거나 마지막으로 센 지 cutoff보다 오래됐으면 1, 그 안에 다시 봤으면 0(세지 않음). 동시에 와도 한 번만 센다.
   */
  @Modifying
  @Query(
      nativeQuery = true,
      value =
          "insert into {h-schema}post_views as v (post_id, viewer_key, viewed_at)"
              + " values (:postId, :viewerKey, :now)"
              + " on conflict (post_id, viewer_key) do update"
              + " set viewed_at = excluded.viewed_at, updated_at = excluded.viewed_at"
              + " where v.viewed_at <= :cutoff")
  int markViewed(Long postId, String viewerKey, Instant now, Instant cutoff);

  @Modifying
  @Query("update Post p set p.viewCount = p.viewCount + 1 where p.id = :postId")
  int addViews(Long postId);

  @Query("select p.viewCount from Post p where p.id = :postId")
  long viewCountOf(Long postId);

  @Modifying
  @Query(nativeQuery = true, value = "delete from {h-schema}post_views where viewed_at < :before")
  int forgetViewsBefore(Instant before);

  @Modifying
  @Query("update Post p set p.commentCount = p.commentCount + :delta where p.id = :postId")
  int addComments(Long postId, int delta);
}
