package net.java21.fourcut.content.comic;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.post.Post;
import net.java21.fourcut.content.post.PostRepository;
import net.java21.fourcut.content.storage.ObjectStorage;
import net.java21.fourcut.content.support.Cursor;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.support.PublicId;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 만화함: 등록(내부), 목록, 상세, 삭제, 이미지 주소. 만화는 주인만 본다. */
@Service
public class ComicService {

  /** 사전 서명 주소 유효 시간. web이 바로 받아 중계하므로 짧게 둔다. */
  public static final Duration IMAGE_URL_TTL = Duration.ofSeconds(60);

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  /** 제작 정보 최대 크기 (JSON 문자 수). 컷이 10개여도 몇 KB라 넉넉히 둔다. */
  static final int MAX_PRODUCTION = 32_000;

  private static final TypeReference<List<CastMember>> CAST = new TypeReference<>() {};

  private final ComicRepository comics;
  private final PostRepository posts;
  private final ObjectStorage storage;
  private final JsonMapper json;
  private final ImportProperties importers;

  public ComicService(
      ComicRepository comics,
      PostRepository posts,
      ObjectStorage storage,
      JsonMapper json,
      ImportProperties importers) {
    this.comics = comics;
    this.posts = posts;
    this.storage = storage;
    this.json = json;
    this.importers = importers;
  }

  /**
   * 이미 그린 만화 그림으로 만화함에 만화를 넣는다 (모델 비교처럼 파이프라인 밖에서 그린 시험 만화). 허용한 사용자만 쓴다(아니면 403). 대화가 없으므로 대화 ID
   * 자리에는 새 ID를 넣어 저장 경로를 나눈다.
   */
  @Transactional
  public ComicView importComic(String owner, ImportComicRequest r) {
    if (!importers.users().contains(owner)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    ComicImages.Decoded image = ComicImages.decode(r.image());
    String conversationId = PublicId.generate();
    String imageKey = "comics/" + conversationId + "/full." + image.extension();
    String thumbKey = "comics/" + conversationId + "/thumb.png";
    storage.put(imageKey, image.bytes(), image.contentType());
    storage.put(thumbKey, ComicImages.thumbnail(image.image()), "image/png");
    Comic comic =
        new Comic(
            owner,
            conversationId,
            r.title(),
            imageKey,
            thumbKey,
            r.language() == null ? "ko" : r.language(),
            "[]");
    comic.describe(
        r.cast() == null ? null : json.writeValueAsString(r.cast()), r.concept(), r.dialect());
    comic.recordProduction(productionJson(r.production()));
    return viewsOf(List.of(comics.save(comic))).get(0);
  }

  /** 완성된 만화를 만화함에 넣는다. 같은 대화로 다시 오면 기존 만화를 돌려준다 (멱등). */
  @Transactional
  public RegisteredComic register(RegisterComicRequest r) {
    return comics
        .findByConversationId(r.conversationId())
        .map(c -> new RegisteredComic(c.getPublicId(), false))
        .orElseGet(
            () -> {
              List<String> warnings = r.nameWarnings() == null ? List.of() : r.nameWarnings();
              Comic comic =
                  new Comic(
                      r.ownerId(),
                      r.conversationId(),
                      r.title(),
                      r.imageKey(),
                      r.thumbKey(),
                      r.language(),
                      json.writeValueAsString(warnings));
              comic.describe(
                  r.cast() == null ? null : json.writeValueAsString(r.cast()),
                  r.concept(),
                  r.dialect());
              comic.recordProduction(productionJson(r.production()));
              Comic saved = comics.save(comic);
              return new RegisteredComic(saved.getPublicId(), true);
            });
  }

  @Transactional(readOnly = true)
  public CursorPage<ComicView> inbox(String owner, String cursor, Integer size) {
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    List<Comic> rows = comics.inbox(owner, from.at(), from.id(), Limit.of(limit + 1));
    return CursorPage.of(rows, limit, c -> new Cursor(c.getCreatedAt(), c.getId()), this::viewsOf);
  }

  @Transactional(readOnly = true)
  public ComicView get(String owner, String comicId) {
    return viewsOf(List.of(owned(owner, comicId))).get(0);
  }

  /** 만화를 지운다. 게시물·좋아요·댓글은 DB에서 함께 지워지고, 이미지도 지운다. */
  @Transactional
  public void delete(String owner, String comicId) {
    remove(owned(owner, comicId));
  }

  /** 만화와 이미지를 지운다. 주인 확인은 부르는 쪽이 한다. */
  @Transactional
  public void remove(Comic comic) {
    storage.delete(List.of(comic.getImageKey(), comic.getThumbKey()));
    comics.delete(comic);
  }

  @Transactional(readOnly = true)
  public ImageUrl image(String owner, String comicId, ImageSize size) {
    return imageOf(owned(owner, comicId), size);
  }

  /** 만화 이미지의 사전 서명 주소. */
  public ImageUrl imageOf(Comic comic, ImageSize size) {
    return new ImageUrl(
        storage.presignGet(size.keyOf(comic), IMAGE_URL_TTL), IMAGE_URL_TTL.toSeconds());
  }

  /** 주인의 만화. 없거나, 남의 것이거나, 형식이 틀린 ID면 404. */
  private Comic owned(String owner, String comicId) {
    if (!PublicId.isValid(comicId)) {
      throw ApiException.notFound();
    }
    return comics
        .findByPublicId(comicId)
        .filter(c -> c.getOwnerId().equals(owner))
        .orElseThrow(ApiException::notFound);
  }

  private List<ComicView> viewsOf(List<Comic> page) {
    List<Long> posted =
        page.stream().filter(c -> c.getState() == Comic.State.POSTED).map(Comic::getId).toList();
    Map<Long, String> postIds =
        posted.isEmpty()
            ? Map.of()
            : posts.findByComicIdIn(posted).stream()
                .collect(Collectors.toMap(Post::getComicId, Post::getPublicId, (a, b) -> a));
    return page.stream()
        .map(
            c ->
                new ComicView(
                    c.getPublicId(),
                    c.getConversationId(),
                    c.getTitle(),
                    c.getLanguage(),
                    warnings(c.getNameWarnings()),
                    c.getState(),
                    postIds.get(c.getId()),
                    c.getCreatedAt(),
                    castOf(c),
                    c.getConcept(),
                    c.getDialect(),
                    productionOf(c)))
        .toList();
  }

  /**
   * 대화로 찾은 만화에 제작 정보를 남긴다 (내부). 예전 만화를 conversation-service가 남은 단계 기록으로 채울 때 쓴다.
   *
   * @param overwrite false면 이미 있는 제작 정보는 그대로 둔다
   * @return 만화가 없으면 false
   */
  @Transactional
  public boolean recordProduction(String conversationId, JsonNode production, boolean overwrite) {
    String raw = productionJson(production);
    if (raw == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    }
    return comics
        .findByConversationId(conversationId)
        .map(
            c -> {
              if (overwrite || c.getProduction() == null) {
                c.recordProduction(raw);
              }
              return true;
            })
        .orElse(false);
  }

  /** 내 만화의 제작 정보를 바꾼다. 남의 만화면 404, 객체가 아니거나 너무 크면 400. */
  @Transactional
  public ComicView replaceProduction(String owner, String comicId, JsonNode production) {
    String raw = productionJson(production);
    if (raw == null) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED);
    }
    Comic comic = owned(owner, comicId);
    comic.recordProduction(raw);
    return viewsOf(List.of(comic)).get(0);
  }

  /** 만화의 제작 정보. 예전 만화는 null. */
  public JsonNode productionOf(Comic comic) {
    String raw = comic.getProduction();
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return json.readTree(raw);
  }

  /** 저장할 제작 정보 JSON. 객체가 아니거나 너무 크면 null (만화 등록은 제작 정보 없이도 된다). */
  private String productionJson(JsonNode production) {
    if (production == null || !production.isObject()) {
      return null;
    }
    String raw = json.writeValueAsString(production);
    return raw.length() > MAX_PRODUCTION ? null : raw;
  }

  /** 만화의 등장인물 소개. 예전 만화는 빈 목록. */
  public List<CastMember> castOf(Comic comic) {
    String raw = comic.getCastMembers();
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return json.readValue(raw, CAST);
  }

  private List<String> warnings(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return json.readValue(raw, STRINGS);
  }
}
