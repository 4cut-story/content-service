package net.java21.fourcut.content.comic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ComicRepository extends JpaRepository<Comic, UUID> {

  Optional<Comic> findByConversationId(UUID conversationId);

  List<Comic> findByOwnerId(UUID ownerId);

  /** 내 만화함, 최신순. */
  @Query(
      "select c from Comic c where c.ownerId = ?1 and (c.createdAt < ?2 or (c.createdAt = ?2 and c.id < ?3))"
          + " order by c.createdAt desc, c.id desc")
  List<Comic> inbox(UUID ownerId, Instant at, UUID id, Limit limit);
}
