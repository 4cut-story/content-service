package net.java21.fourcut.content.comic;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.post.PostService;
import net.java21.fourcut.content.support.PublicId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** conversation-service → content-service 내부 API. 게이트웨이는 이 경로를 밖으로 열지 않는다. */
@RestController
@Validated
public class InternalComicController {

  private final ComicService comics;
  private final PostService posts;

  public InternalComicController(ComicService comics, PostService posts) {
    this.comics = comics;
    this.posts = posts;
  }

  /**
   * 완성된 만화 등록. 새로 만들면 201, 같은 대화로 이미 있으면 200과 기존 ID. autoPublish면 새로 만든 만화만 전체 공개로 게시한다(다시 등록해도
   * 게시물은 하나). 게시가 실패해도 만화는 만화함에 남는다.
   */
  @PostMapping("/internal/comics")
  public ResponseEntity<Map<String, String>> register(
      @Valid @RequestBody RegisterComicRequest request) {
    RegisteredComic result = comics.register(request);
    if (result.created() && Boolean.TRUE.equals(request.autoPublish())) {
      posts.autoPublish(request.ownerId(), result.comicId(), request.title());
    }
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(Map.of("comicId", result.comicId()));
  }

  /**
   * 대화로 찾은 만화에 제작 정보를 남긴다. 예전 만화를 남은 단계 기록으로 채울 때 쓴다. 없는 대화면 404.
   *
   * @param overwrite true면 이미 있는 제작 정보도 바꾼다. 기본은 비어 있을 때만 채운다
   */
  @PutMapping("/internal/comics/by-conversation/{conversationId}/production")
  public ResponseEntity<Void> production(
      @PathVariable String conversationId,
      @RequestParam(defaultValue = "false") boolean overwrite,
      @RequestBody JsonNode production) {
    if (!PublicId.isValid(conversationId)
        || !comics.recordProduction(conversationId, production, overwrite)) {
      throw ApiException.notFound();
    }
    return ResponseEntity.noContent().build();
  }

  /**
   * 대화로 찾은 만화에 줄거리 요약을 남긴다. 예전 만화를 남은 콘티로 채울 때 쓴다. 없는 대화면 404, 비었거나 너무 길면 400.
   *
   * @param overwrite true면 이미 있는 요약도 바꾼다. 기본은 비어 있을 때만 채운다
   */
  @PutMapping("/internal/comics/by-conversation/{conversationId}/summary")
  public ResponseEntity<Void> summary(
      @PathVariable String conversationId,
      @RequestParam(defaultValue = "false") boolean overwrite,
      @Valid @RequestBody SummaryRequest request) {
    if (!PublicId.isValid(conversationId)
        || !comics.recordSummary(conversationId, request.summary(), overwrite)) {
      throw ApiException.notFound();
    }
    return ResponseEntity.noContent().build();
  }

  /** 줄거리 요약 본문. */
  record SummaryRequest(@NotBlank @Size(max = Comic.MAX_SUMMARY) String summary) {}

  /** 대화로 찾은 만화의 등장인물 소개를 바꾼다. 예전 만화에 얼굴 그림을 채울 때 쓴다. 없는 대화면 404, 본문이 틀리면 400. */
  @PutMapping("/internal/comics/by-conversation/{conversationId}/cast")
  public ResponseEntity<Void> cast(
      @PathVariable String conversationId, @RequestBody List<@Valid CastMember> cast) {
    if (cast.size() > MAX_CAST) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    }
    if (!PublicId.isValid(conversationId) || !comics.recordCast(conversationId, cast)) {
      throw ApiException.notFound();
    }
    return ResponseEntity.noContent().build();
  }

  /** 대화로 찾은 만화의 라이브 재생 정보를 바꾼다(FR-047). 없는 대화면 404, 재생 정보 형식이 틀리면 400. */
  @PutMapping("/internal/comics/by-conversation/{conversationId}/live")
  public ResponseEntity<Void> live(
      @PathVariable String conversationId, @RequestBody JsonNode live) {
    if (!PublicId.isValid(conversationId)) {
      throw ApiException.notFound();
    }
    switch (comics.recordLive(conversationId, live)) {
      case NOT_FOUND -> throw ApiException.notFound();
      case INVALID -> throw new ApiException(ErrorCode.VALIDATION_FAILED);
      case UPDATED -> {
        // 바꿨다
      }
    }
    return ResponseEntity.noContent().build();
  }

  /** 등록 본문과 같은 등장인물 수 한도. */
  static final int MAX_CAST = 10;
}
