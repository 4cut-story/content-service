package net.java21.fourcut.content.comment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommentRepository extends JpaRepository<Comment, Long> {

  /** 게시물 댓글, 오래된 순. */
  @Query(
      "select c from Comment c where c.postId = ?1 and (c.createdAt > ?2 or (c.createdAt = ?2 and c.id > ?3))"
          + " order by c.createdAt asc, c.id asc")
  List<Comment> page(Long postId, Instant at, long id, Limit limit);

  List<Comment> findByAuthorId(String authorId);

  Optional<Comment> findByPublicId(String publicId);
}
