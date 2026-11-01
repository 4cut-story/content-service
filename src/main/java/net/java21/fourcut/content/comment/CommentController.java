package net.java21.fourcut.content.comment;

import jakarta.validation.Valid;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.web.HeaderNames;
import net.java21.fourcut.content.web.Viewer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 댓글. */
@RestController
public class CommentController {

  private final CommentService comments;

  public CommentController(CommentService comments) {
    this.comments = comments;
  }

  @GetMapping("/api/v1/posts/{postId}/comments")
  public CursorPage<CommentView> list(
      @RequestHeader(value = HeaderNames.USER_ID, required = false) String userId,
      @PathVariable String postId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return comments.list(Viewer.of(userId), postId, cursor, size);
  }

  @PostMapping("/api/v1/posts/{postId}/comments")
  public ResponseEntity<CommentView> add(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String postId,
      @Valid @RequestBody CommentRequest body) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(comments.add(userId, postId, body.text()));
  }

  @DeleteMapping("/api/v1/comments/{id}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    comments.delete(userId, id);
    return ResponseEntity.noContent().build();
  }
}
