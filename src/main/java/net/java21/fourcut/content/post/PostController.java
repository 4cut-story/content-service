package net.java21.fourcut.content.post;

import jakarta.validation.Valid;
import java.util.Locale;
import java.util.Map;
import net.java21.fourcut.content.comic.ImageSize;
import net.java21.fourcut.content.comic.ImageUrl;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.web.HeaderNames;
import net.java21.fourcut.content.web.Viewer;
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
import tools.jackson.databind.JsonNode;

/** 게시물, 피드, 좋아요. */
@RestController
public class PostController {

  private final PostService posts;

  public PostController(PostService posts) {
    this.posts = posts;
  }

  @PostMapping("/api/v1/posts")
  public ResponseEntity<Map<String, String>> publish(
      @RequestHeader(HeaderNames.USER_ID) String userId, @Valid @RequestBody PublishRequest body) {
    String postId;
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
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @RequestParam String mode) {
    posts.remove(userId, id, parseMode(mode));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/posts/{id}")
  public PostView detail(
      @RequestHeader(value = HeaderNames.USER_ID, required = false) String userId,
      @PathVariable String id) {
    return posts.detail(Viewer.of(userId), id);
  }

  @GetMapping("/api/v1/feed")
  public CursorPage<PostView> feed(
      @RequestHeader(value = HeaderNames.USER_ID, required = false) String userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String scope) {
    if (scope == null || scope.isBlank() || "all".equals(scope)) {
      return posts.feed(Viewer.of(userId), cursor, size);
    }
    if ("following".equals(scope)) {
      return posts.followingFeed(Viewer.of(userId), cursor, size);
    }
    throw new ApiException(ErrorCode.VALIDATION_FAILED, "scope는 all 또는 following이어야 해요.");
  }

  /** 프로필 게시물. 보는 사람 기준 공개 범위를 적용한다 (로그인하지 않았으면 전체 공개만). */
  @GetMapping("/api/v1/posts")
  public CursorPage<PostView> byAuthor(
      @RequestHeader(value = HeaderNames.USER_ID, required = false) String userId,
      @RequestParam String authorId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return posts.byAuthor(Viewer.of(userId), authorId, cursor, size);
  }

  /** 게시물 이미지의 사전 서명 주소 (web만 쓴다). cast가 있으면 그 순서(0부터) 등장인물의 얼굴 그림. */
  @GetMapping("/api/v1/posts/{id}/image")
  public ImageUrl image(
      @RequestHeader(value = HeaderNames.USER_ID, required = false) String userId,
      @PathVariable String id,
      @RequestParam(required = false) String size,
      @RequestParam(required = false) Integer cast,
      @RequestParam(required = false) Integer panel,
      @RequestParam(required = false) String frame) {
    return posts.image(Viewer.of(userId), id, ImageSize.parse(size), cast, panel, frame);
  }

  /** 게시물의 라이브 모드 재생 정보 (003-live-mode). 볼 수 없거나 예전 만화면 404. */
  @GetMapping("/api/v1/posts/{id}/live")
  public JsonNode live(
      @RequestHeader(value = HeaderNames.USER_ID, required = false) String userId,
      @PathVariable String id) {
    return posts.live(Viewer.of(userId), id);
  }

  @PutMapping("/api/v1/posts/{id}/like")
  public ResponseEntity<Void> like(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
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
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
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
