package net.java21.fourcut.content.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import net.java21.fourcut.content.support.BaseEntity;

/** 게시물 = 만화 1장 + 캡션 1줄 + 공개 범위. 만화와 1:1. */
@Entity
@Table(name = "posts")
public class Post extends BaseEntity {

  @Column(name = "comic_id", nullable = false)
  private UUID comicId;

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(length = 100)
  private String caption;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Visibility visibility;

  @Column(name = "participants_consented", nullable = false)
  private boolean participantsConsented;

  @Column(name = "published_at", nullable = false)
  private Instant publishedAt;

  @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
  private int likeCount;

  @Column(name = "comment_count", nullable = false, insertable = false, updatable = false)
  private int commentCount;

  protected Post() {
    // JPA 전용
  }

  public Post(
      UUID comicId, UUID authorId, String caption, Visibility visibility, Instant publishedAt) {
    this.comicId = comicId;
    this.authorId = authorId;
    this.caption = caption;
    this.visibility = visibility;
    this.participantsConsented = true;
    this.publishedAt = publishedAt;
  }

  public UUID getComicId() {
    return comicId;
  }

  public UUID getAuthorId() {
    return authorId;
  }

  public String getCaption() {
    return caption;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }

  public int getLikeCount() {
    return likeCount;
  }

  public int getCommentCount() {
    return commentCount;
  }
}
