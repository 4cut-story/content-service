package net.java21.fourcut.content.post;

import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.java21.fourcut.content.comic.ImageSize;
import net.java21.fourcut.content.comic.ImageUrl;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.web.HeaderNames;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 게시물, 피드, 좋아요. */
@RestController
public class PostController {

  private final PostService posts;

  public PostController(PostService posts) {
    this.posts = posts;
  }

  @PostMapping("/api/v1/posts")
  public ResponseEntity<Map<String, UUID>> publish(
      @RequestHeader(HeaderNames.USER_ID) UUID userId, @Valid @RequestBody PublishRequest body) {
    UUID postId;
    try {
      postId = posts.publish(userId, body);
    } catch (DataIntegrityViolationException e) {
      // 같은 만화를 동시에 두 번 올린 경우
      throw new ApiException(ErrorCode.ALREADY_PUBLISHED, ErrorCode.ALREADY_PUBLISHED.message(), e);
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("postId", postId));
  }

  @DeleteMapping("/api/v1/posts/{id}")
  public ResponseEntity<Void> remove(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @PathVariable UUID id,
      @RequestParam String mode) {
    posts.remove(userId, id, parseMode(mode));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/posts/{id}")
  public PostView detail(@RequestHeader(HeaderNames.USER_ID) UUID userId, @PathVariable UUID id) {
    return posts.detail(userId, id);
  }

  @GetMapping("/api/v1/feed")
  public CursorPage<PostView> feed(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return posts.feed(userId, cursor, size);
  }

  /** 프로필 게시물. 보는 사람 기준 공개 범위를 적용한다. */
  @GetMapping("/api/v1/posts")
  public CursorPage<PostView> byAuthor(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @RequestParam UUID authorId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return posts.byAuthor(userId, authorId, cursor, size);
  }

  /** 게시물 이미지의 사전 서명 주소 (web만 쓴다). */
  @GetMapping("/api/v1/posts/{id}/image")
  public ImageUrl image(
      @RequestHeader(HeaderNames.USER_ID) UUID userId,
      @PathVariable UUID id,
      @RequestParam(required = false) String size) {
    return posts.image(userId, id, ImageSize.parse(size));
  }

  @PutMapping("/api/v1/posts/{id}/like")
  public ResponseEntity<Void> like(
      @RequestHeader(HeaderNames.USER_ID) UUID userId, @PathVariable UUID id) {
    try {
      posts.like(userId, id);
    } catch (DataIntegrityViolationException e) {
      // 동시에 두 번 누른 경우. 이미 좋아요가 있으므로 성공으로 본다
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/v1/posts/{id}/like")
  public ResponseEntity<Void> unlike(
      @RequestHeader(HeaderNames.USER_ID) UUID userId, @PathVariable UUID id) {
    posts.unlike(userId, id);
    return ResponseEntity.noContent().build();
  }

  static PostService.RemoveMode parseMode(String mode) {
    try {
      return PostService.RemoveMode.valueOf(mode.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "mode는 unpublish 또는 delete여야 해요.", e);
    }
  }
}
