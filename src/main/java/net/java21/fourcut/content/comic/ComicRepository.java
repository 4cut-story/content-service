package net.java21.fourcut.content.comic;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ComicRepository extends JpaRepository<Comic, Long> {

  Optional<Comic> findByConversationId(String conversationId);

  List<Comic> findByOwnerId(String ownerId);

  long countByOwnerId(String ownerId);

  Optional<Comic> findByPublicId(String publicId);

  /** 내 만화함, 최신순. */
  @Query(
      "select c from Comic c where c.ownerId = ?1 and (c.createdAt < ?2 or (c.createdAt = ?2 and c.id < ?3))"
          + " order by c.createdAt desc, c.id desc")
  List<Comic> inbox(String ownerId, Instant at, long id, Limit limit);

  /** 목록 썸네일이 아직 없는 만화, 최신순. 주기 작업이 채운다. */
  @Query("select c from Comic c where c.coverKey is null order by c.id desc")
  List<Comic> withoutCover(Limit limit);

  /** 목록 썸네일 키를 남긴다. 읽기 전용 요청 안에서도 따로 남기려고 엔티티를 거치지 않는다. */
  @Modifying
  @Query("update Comic c set c.coverKey = ?2 where c.id = ?1")
  int recordCover(long id, String coverKey);
}
