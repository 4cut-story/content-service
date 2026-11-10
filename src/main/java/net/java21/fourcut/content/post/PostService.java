package net.java21.fourcut.content.post;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.java21.fourcut.content.comic.Comic;
import net.java21.fourcut.content.comic.ComicRepository;
import net.java21.fourcut.content.comic.ComicService;
import net.java21.fourcut.content.comic.ImageSize;
import net.java21.fourcut.content.comic.ImageUrl;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.support.Cursor;
import net.java21.fourcut.content.support.CursorPage;
import net.java21.fourcut.content.support.PublicId;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/** 올리기·내리기, 피드, 프로필 게시물, 상세, 좋아요. 볼 수 없는 게시물은 404다. */
@Service
public class PostService {

  /** 게시물 지우기 방식: 만화함으로 내리기 또는 만화까지 삭제. */
  public enum RemoveMode {
    UNPUBLISH,
    DELETE
  }

  private final PostRepository posts;
  private final PostLikeRepository likes;
  private final ComicRepository comics;
  private final ComicService comicService;
  private final Clock clock;

  public PostService(
      PostRepository posts,
      PostLikeRepository likes,
      ComicRepository comics,
      ComicService comicService,
      Clock clock) {
    this.posts = posts;
    this.likes = likes;
    this.comics = comics;
    this.comicService = comicService;
    this.clock = clock;
  }

  /** 만화함의 만화를 올린다. 참여자 동의가 없으면 올리지 않는다. */
  @Transactional
  public String publish(String author, PublishRequest r) {
    if (!Boolean.TRUE.equals(r.participantsConsented())) {
      throw new ApiException(ErrorCode.CONSENT_REQUIRED);
    }
    Comic comic =
        comics
            .findByPublicId(r.comicId())
            .filter(c -> c.getOwnerId().equals(author))
            .orElseThrow(ApiException::notFound);
    if (comic.getState() == Comic.State.POSTED) {
      throw new ApiException(ErrorCode.ALREADY_PUBLISHED);
    }
    String caption = r.caption() == null || r.caption().isBlank() ? null : r.caption().strip();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Post post = posts.save(new Post(comic.getId(), author, caption, r.visibility(), now));
    comic.markPosted();
    return post.getPublicId();
  }

  /**
   * 사용자가 미리 고른 "바로 공개"(RSS 구독)로 만든 만화를 전체 공개로 올린다. 캡션은 만화 제목이다. 기사로 만든 만화라 함께 대화한 사람의 동의가 필요 없다. 이미
   * 올라가 있으면 그대로 둔다.
   *
   * @return 게시물 공개 ID. 이미 올라가 있었거나 만화가 없으면 null
   */
  @Transactional
  public String autoPublish(String owner, String comicId, String caption) {
    Comic comic =
        comics.findByPublicId(comicId).filter(c -> c.getOwnerId().equals(owner)).orElse(null);
    if (comic == null || comic.getState() == Comic.State.POSTED) {
      return null;
    }
    return publish(owner, new PublishRequest(comicId, caption, Visibility.PUBLIC, true));
  }

  /** 내 게시물을 내리거나(만화함으로) 만화째 지운다. 좋아요·댓글은 함께 지워진다. */
  @Transactional
  public void remove(String author, String postId, RemoveMode mode) {
    Post post = visible(author, postId);
    if (!post.getAuthorId().equals(author)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (mode == RemoveMode.DELETE) {
      comics.findById(post.getComicId()).ifPresent(comicService::remove);
      return;
    }
    posts.delete(post);
    comics.findById(post.getComicId()).ifPresent(Comic::backToInbox);
  }

  @Transactional(readOnly = true)
  public PostView detail(String viewer, String postId) {
    return viewsOf(viewer, List.of(visible(viewer, postId))).get(0);
  }

  @Transactional(readOnly = true)
  public CursorPage<PostView> feed(String viewer, String cursor, Integer size) {
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return page(viewer, limit, posts.feed(viewer, from.at(), from.id(), Limit.of(limit + 1)));
  }

  @Transactional(readOnly = true)
  public CursorPage<PostView> byAuthor(String viewer, String author, String cursor, Integer size) {
    if (!PublicId.isValid(author)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "authorId 형식이 올바르지 않아요.");
    }
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return page(
        viewer, limit, posts.byAuthor(viewer, author, from.at(), from.id(), Limit.of(limit + 1)));
  }

  @Transactional(readOnly = true)
  public ImageUrl image(String viewer, String postId, ImageSize size, Integer cast) {
    return image(viewer, postId, size, cast, null, null);
  }

  /** 게시물 이미지. panel이 있으면 라이브 모드 컷 그림(frame b·c면 움직임 컷의 두 번째·세 번째 그림). */
  @Transactional(readOnly = true)
  public ImageUrl image(
      String viewer, String postId, ImageSize size, Integer cast, Integer panel, String frame) {
    Comic comic = comicOf(viewer, postId);
    if (panel != null) {
      return comicService.panelImageOf(comic, panel, frame, cast);
    }
    return cast == null ? comicService.imageOf(comic, size) : comicService.castImageOf(comic, cast);
  }

  /** 볼 수 있는 게시물의 라이브 모드 재생 정보. 볼 수 없거나 없으면 404. */
  @Transactional(readOnly = true)
  public JsonNode live(String viewer, String postId) {
    return comicService.liveOf(comicOf(viewer, postId));
  }

  private Comic comicOf(String viewer, String postId) {
    Post post = visible(viewer, postId);
    return comics.findById(post.getComicId()).orElseThrow(ApiException::notFound);
  }

  /** 좋아요 (멱등). */
  @Transactional
  public void like(String viewer, String postId) {
    Long id = visible(viewer, postId).getId();
    if (!likes.existsByPostIdAndUserId(id, viewer)) {
      likes.saveAndFlush(new PostLike(id, viewer));
      posts.addLikes(id, 1);
    }
  }

  /** 좋아요 취소 (멱등). */
  @Transactional
  public void unlike(String viewer, String postId) {
    Long id = visible(viewer, postId).getId();
    if (likes.deleteByPostIdAndUserId(id, viewer) > 0) {
      posts.addLikes(id, -1);
    }
  }

  /** 보는 사람이 볼 수 있는 게시물 (공개 ID로 찾는다). 없거나, 볼 수 없거나, 형식이 틀린 ID면 404. */
  public Post visible(String viewer, String postId) {
    if (!PublicId.isValid(postId)) {
      throw ApiException.notFound();
    }
    Post post = posts.findByPublicId(postId).orElseThrow(ApiException::notFound);
    return visible(viewer, post);
  }

  /** 보는 사람이 볼 수 있는 게시물. 볼 수 없으면 404. */
  public Post visible(String viewer, Post post) {
    if (!posts.visibleTo(post.getId(), viewer)) {
      throw ApiException.notFound();
    }
    return post;
  }

  private CursorPage<PostView> page(String viewer, int limit, List<Post> rows) {
    return CursorPage.of(
        rows, limit, p -> new Cursor(p.getPublishedAt(), p.getId()), page -> viewsOf(viewer, page));
  }

  private List<PostView> viewsOf(String viewer, List<Post> page) {
    if (page.isEmpty()) {
      return List.of();
    }
    List<Long> ids = page.stream().map(Post::getId).toList();
    Set<Long> liked = new HashSet<>(likes.likedPostIds(viewer, ids));
    Map<Long, Comic> comicsById =
        comics.findAllById(page.stream().map(Post::getComicId).toList()).stream()
            .collect(Collectors.toMap(Comic::getId, Function.identity()));
    return page.stream()
        .map(
            p -> {
              Comic comic = comicsById.get(p.getComicId());
              return new PostView(
                  p.getPublicId(),
                  comic == null ? null : comic.getPublicId(),
                  p.getAuthorId(),
                  comic == null ? null : comic.getTitle(),
                  p.getCaption(),
                  p.getVisibility(),
                  p.getPublishedAt(),
                  p.getLikeCount(),
                  p.getCommentCount(),
                  p.getViewCount(),
                  liked.contains(p.getId()),
                  comic == null ? List.of() : comicService.castViewsOf(comic),
                  comic == null ? null : comic.getConcept(),
                  comic == null ? null : comic.getDialect(),
                  comic == null ? null : comic.getSummary(),
                  comic == null ? null : comicService.productionOf(comic),
                  comic != null && comic.getLive() != null);
            })
        .toList();
  }
}
