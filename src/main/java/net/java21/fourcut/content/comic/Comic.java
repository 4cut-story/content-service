package net.java21.fourcut.content.comic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import net.java21.fourcut.content.support.BaseEntity;
import net.java21.fourcut.content.support.PublicId;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 만화. 생성되면 만화함(IN_INBOX)에 들어가고, 사용자가 올리면 POSTED가 된다. */
@Entity
@Table(name = "comics")
public class Comic extends BaseEntity {

  /** 만화 상태. */
  public enum State {
    IN_INBOX,
    POSTED
  }

  /** 밖에 보이는 만화 ID. */
  @Column(name = "public_id", nullable = false, updatable = false, length = PublicId.LENGTH)
  private String publicId = PublicId.generate();

  /** 소유자의 공개 ID (auth). */
  @Column(name = "owner_id", nullable = false, length = PublicId.LENGTH)
  private String ownerId;

  /** 대화의 공개 ID (conversation). */
  @Column(name = "conversation_id", nullable = false, length = PublicId.LENGTH)
  private String conversationId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(name = "image_key", nullable = false, length = 500)
  private String imageKey;

  @Column(name = "thumb_key", nullable = false, length = 500)
  private String thumbKey;

  @Column(nullable = false, length = 2)
  private String language;

  /** 대사에 남은 실명 의심 단어 (JSON 배열). 올리기 전 경고에 쓴다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "name_warnings", columnDefinition = "json")
  private String nameWarnings;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private State state = State.IN_INBOX;

  protected Comic() {
    // JPA 전용
  }

  public Comic(
      String ownerId,
      String conversationId,
      String title,
      String imageKey,
      String thumbKey,
      String language,
      String nameWarnings) {
    this.ownerId = ownerId;
    this.conversationId = conversationId;
    this.title = title;
    this.imageKey = imageKey;
    this.thumbKey = thumbKey;
    this.language = language;
    this.nameWarnings = nameWarnings;
  }

  public String getPublicId() {
    return publicId;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public String getConversationId() {
    return conversationId;
  }

  public String getTitle() {
    return title;
  }

  public String getImageKey() {
    return imageKey;
  }

  public String getThumbKey() {
    return thumbKey;
  }

  public String getLanguage() {
    return language;
  }

  public String getNameWarnings() {
    return nameWarnings;
  }

  public State getState() {
    return state;
  }

  public void markPosted() {
    this.state = State.POSTED;
  }

  public void backToInbox() {
    this.state = State.IN_INBOX;
  }
}
