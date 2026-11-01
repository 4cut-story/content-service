package net.java21.fourcut.content.comic;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.java21.fourcut.content.error.ApiException;
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
import tools.jackson.databind.json.JsonMapper;

/** 만화함: 등록(내부), 목록, 상세, 삭제, 이미지 주소. 만화는 주인만 본다. */
@Service
public class ComicService {

  /** 사전 서명 주소 유효 시간. web이 바로 받아 중계하므로 짧게 둔다. */
  public static final Duration IMAGE_URL_TTL = Duration.ofSeconds(60);

  private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {};

  private final ComicRepository comics;
  private final PostRepository posts;
  private final ObjectStorage storage;
  private final JsonMapper json;

  public ComicService(
      ComicRepository comics, PostRepository posts, ObjectStorage storage, JsonMapper json) {
    this.comics = comics;
    this.posts = posts;
    this.storage = storage;
    this.json = json;
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
              Comic saved =
                  comics.save(
                      new Comic(
                          r.ownerId(),
                          r.conversationId(),
                          r.title(),
                          r.imageKey(),
                          r.thumbKey(),
                          r.language(),
                          json.writeValueAsString(warnings)));
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
                    c.getCreatedAt()))
        .toList();
  }

  private List<String> warnings(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    return json.readValue(raw, STRINGS);
  }
}
