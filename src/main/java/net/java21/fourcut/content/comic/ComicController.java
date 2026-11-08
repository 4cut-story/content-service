package net.java21.fourcut.content.comic;

import jakarta.validation.Valid;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.web.HeaderNames;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

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

  /** 이미 그린 만화 그림으로 만화함에 넣는다 (허용한 사용자만, 파이프라인 밖에서 그린 시험 만화). */
  @PostMapping
  public ResponseEntity<ComicView> importComic(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @Valid @RequestBody ImportComicRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(comics.importComic(userId, request));
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

  /**
   * 내 만화의 제작 정보를 바꾼다. 파이프라인 밖에서 그린 시험 만화처럼 서버가 모르는 모델·조건·시간·점수를 남길 때 쓴다. 형식은 conversation-service
   * 제작 정보(version 1)와 같다.
   */
  @PutMapping("/{id}/production")
  public ComicView production(
      @RequestHeader(HeaderNames.USER_ID) String userId,
      @PathVariable String id,
      @RequestBody JsonNode production) {
    return comics.replaceProduction(userId, id, production);
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
