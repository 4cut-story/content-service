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

  /** 줄거리 요약 최대 길이 (V4 마이그레이션 summary 컬럼). */
  public static final int MAX_SUMMARY = 1000;

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

  /** 목록 썸네일(첫 컷 JPEG) 객체 키. 아직 만들지 않았으면 null이고, 처음 요청하거나 주기 작업이 만든다. */
  @Column(name = "cover_key", length = 500)
  private String coverKey;

  @Column(nullable = false, length = 2)
  private String language;

  /** 대사에 남은 실명 의심 단어 (JSON 배열). 올리기 전 경고에 쓴다. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "name_warnings", columnDefinition = "json")
  private String nameWarnings;

  /** 등장인물 소개 (JSON 배열: role, name, lookId). 예전 만화는 null. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "cast_members", columnDefinition = "json")
  private String castMembers;

  /** 그린 그림체. 예전 만화는 null. */
  @Column(length = 20)
  private String concept;

  /** 대사 사투리. 예전 만화는 null. */
  @Column(length = 20)
  private String dialect;

  /** 제작 정보 (JSON 객체: 단계별 모델·조건·걸린 시간). conversation-service가 만든 그대로 둔다. 예전 만화는 null. */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String production;

  /** 만화 아래 줄거리 요약. 예전 만화는 null. */
  @Column(length = MAX_SUMMARY)
  private String summary;

  /** 라이브 모드 재생 정보 (JSON 객체, common contracts/live-mode.yaml LivePlayback). 예전 만화는 null(만화보기만). */
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "json")
  private String live;

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

  /** 등장인물 소개와 만든 옵션을 남긴다. */
  public void describe(String castMembers, String concept, String dialect) {
    this.castMembers = castMembers;
    this.concept = concept;
    this.dialect = dialect;
  }

  /** 제작 정보를 남긴다. 이미 있으면 덮어쓴다. */
  public void recordProduction(String production) {
    this.production = production;
  }

  /** 줄거리 요약을 남긴다. 이미 있으면 덮어쓴다. */
  public void recordSummary(String summary) {
    this.summary = summary;
  }

  public String getSummary() {
    return summary;
  }

  /** 라이브 모드 재생 정보를 남긴다. */
  public void recordLive(String live) {
    this.live = live;
  }

  public String getLive() {
    return live;
  }

  public String getProduction() {
    return production;
  }

  public String getCastMembers() {
    return castMembers;
  }

  public String getConcept() {
    return concept;
  }

  public String getDialect() {
    return dialect;
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

  public String getCoverKey() {
    return coverKey;
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
