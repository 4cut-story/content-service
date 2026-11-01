package net.java21.fourcut.content.comic;

import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.web.HeaderNames;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내 만화함. 다른 사람의 만화는 없는 것(404)으로 본다. */
@RestController
@RequestMapping("/api/v1/comics")
public class ComicController {

  private final ComicService comics;

  public ComicController(ComicService comics) {
    this.comics = comics;
  }

  @GetMapping
  public CursorPage<ComicView> inbox(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) Integer size) {
    return comics.inbox(userId, cursor, size);
  }

  @GetMapping("/{id}")
  public ComicView get(@RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    return comics.get(userId, id);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @RequestHeader(HeaderNames.USER_ID) String userId, @PathVariable String id) {
    comics.delete(userId, id);
    return ResponseEntity.noContent().build();
  }

  /** 만화함 이미지 (올리기 전 미리보기). web만 쓴다. */
  @GetMapping("/{id}/image")
  public ImageUrl image(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @RequestParam(required = false) String size) {
    return comics.image(userId, id, ImageSize.parse(size));
  }
}
