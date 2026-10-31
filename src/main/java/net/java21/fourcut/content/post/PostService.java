package net.java21.fourcut.content.post;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
  public UUID publish(UUID author, PublishRequest r) {
    if (!Boolean.TRUE.equals(r.participantsConsented())) {
      throw new ApiException(ErrorCode.CONSENT_REQUIRED);
    }
    Comic comic =
        comics
            .findById(r.comicId())
            .filter(c -> c.getOwnerId().equals(author))
            .orElseThrow(ApiException::notFound);
    if (comic.getState() == Comic.State.POSTED) {
      throw new ApiException(ErrorCode.ALREADY_PUBLISHED);
    }
    String caption = r.caption() == null || r.caption().isBlank() ? null : r.caption().strip();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Post post = posts.save(new Post(comic.getId(), author, caption, r.visibility(), now));
    comic.markPosted();
    return post.getId();
  }

  /** 내 게시물을 내리거나(만화함으로) 만화째 지운다. 좋아요·댓글은 함께 지워진다. */
  @Transactional
  public void remove(UUID author, UUID postId, RemoveMode mode) {
    Post post = visible(author, postId);
    if (!post.getAuthorId().equals(author)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    if (mode == RemoveMode.DELETE) {
      comicService.delete(author, post.getComicId());
      return;
    }
    posts.delete(post);
    comics.findById(post.getComicId()).ifPresent(Comic::backToInbox);
  }

  @Transactional(readOnly = true)
  public PostView detail(UUID viewer, UUID postId) {
    return viewsOf(viewer, List.of(visible(viewer, postId))).get(0);
  }

  @Transactional(readOnly = true)
  public CursorPage<PostView> feed(UUID viewer, String cursor, Integer size) {
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return page(viewer, limit, posts.feed(viewer, from.at(), from.id(), Limit.of(limit + 1)));
  }

  @Transactional(readOnly = true)
  public CursorPage<PostView> byAuthor(UUID viewer, UUID author, String cursor, Integer size) {
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.NEWEST_FIRST);
    return page(
        viewer, limit, posts.byAuthor(viewer, author, from.at(), from.id(), Limit.of(limit + 1)));
  }

  @Transactional(readOnly = true)
  public ImageUrl image(UUID viewer, UUID postId, ImageSize size) {
    Post post = visible(viewer, postId);
    Comic comic = comics.findById(post.getComicId()).orElseThrow(ApiException::notFound);
    return comicService.imageOf(comic, size);
  }

  /** 좋아요 (멱등). */
  @Transactional
  public void like(UUID viewer, UUID postId) {
    visible(viewer, postId);
    if (!likes.existsByPostIdAndUserId(postId, viewer)) {
      likes.saveAndFlush(new PostLike(postId, viewer));
      posts.addLikes(postId, 1);
    }
  }

  /** 좋아요 취소 (멱등). */
  @Transactional
  public void unlike(UUID viewer, UUID postId) {
    visible(viewer, postId);
    if (likes.deleteByPostIdAndUserId(postId, viewer) > 0) {
      posts.addLikes(postId, -1);
    }
  }

  /** 보는 사람이 볼 수 있는 게시물. 없거나 볼 수 없으면 404. */
  public Post visible(UUID viewer, UUID postId) {
    if (!posts.visibleTo(postId, viewer)) {
      throw ApiException.notFound();
    }
    return posts.findById(postId).orElseThrow(ApiException::notFound);
  }

  private CursorPage<PostView> page(UUID viewer, int limit, List<Post> rows) {
    return CursorPage.of(
        rows, limit, p -> new Cursor(p.getPublishedAt(), p.getId()), page -> viewsOf(viewer, page));
  }

  private List<PostView> viewsOf(UUID viewer, List<Post> page) {
    if (page.isEmpty()) {
      return List.of();
    }
    List<UUID> ids = page.stream().map(Post::getId).toList();
    Set<UUID> liked = new HashSet<>(likes.likedPostIds(viewer, ids));
    Map<UUID, Comic> comicsById =
        comics.findAllById(page.stream().map(Post::getComicId).toList()).stream()
            .collect(Collectors.toMap(Comic::getId, Function.identity()));
    return page.stream()
        .map(
            p -> {
              Comic comic = comicsById.get(p.getComicId());
              return new PostView(
                  p.getId(),
                  p.getComicId(),
                  p.getAuthorId(),
                  comic == null ? null : comic.getTitle(),
                  p.getCaption(),
                  p.getVisibility(),
                  p.getPublishedAt(),
                  p.getLikeCount(),
                  p.getCommentCount(),
                  liked.contains(p.getId()));
            })
        .toList();
  }
}
