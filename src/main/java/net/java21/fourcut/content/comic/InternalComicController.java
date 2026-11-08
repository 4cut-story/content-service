package net.java21.fourcut.content.comic;

import jakarta.validation.Valid;
import java.util.Map;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.support.PublicId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** conversation-service → content-service 내부 API. 게이트웨이는 이 경로를 밖으로 열지 않는다. */
@RestController
public class InternalComicController {

  private final ComicService comics;

  public InternalComicController(ComicService comics) {
    this.comics = comics;
  }

  /** 완성된 만화 등록. 새로 만들면 201, 같은 대화로 이미 있으면 200과 기존 ID. */
  @PostMapping("/internal/comics")
  public ResponseEntity<Map<String, String>> register(
      @Valid @RequestBody RegisterComicRequest request) {
    RegisteredComic result = comics.register(request);
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
}
