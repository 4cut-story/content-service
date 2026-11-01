package net.java21.fourcut.content.follow;

import net.java21.fourcut.content.web.HeaderNames;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 팔로우. 사용자 관련이지만 {@code /api/v1/users/**}는 auth-service 전용이라 여기에 둔다. */
@RestController
public class FollowController {

  private final FollowService follows;

  public FollowController(FollowService follows) {
    this.follows = follows;
  }

  @PutMapping("/api/v1/follows/{userId}")
  public ResponseEntity<Void> follow(
      @RequestHeader(HeaderNames.USER_ID) String viewer, @PathVariable String userId) {
    try {
      follows.follow(viewer, userId);
    } catch (DataIntegrityViolationException e) {
      // 동시에 두 번 누른 경우. 이미 팔로우했으므로 성공으로 본다
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/api/v1/follows/{userId}")
  public ResponseEntity<Void> unfollow(
      @RequestHeader(HeaderNames.USER_ID) String viewer, @PathVariable String userId) {
    follows.unfollow(viewer, userId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/api/v1/follows/{userId}/stats")
  public FollowStats stats(
      @RequestHeader(HeaderNames.USER_ID) String viewer, @PathVariable String userId) {
    return follows.stats(viewer, userId);
  }
}
