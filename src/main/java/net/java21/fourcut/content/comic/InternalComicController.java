package net.java21.fourcut.content.comic;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
}
