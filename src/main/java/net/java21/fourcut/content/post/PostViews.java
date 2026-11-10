package net.java21.fourcut.content.post;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만화별 조회수. 게시물 상세를 연 방문자(web이 붙인 방문자 쿠키의 해시)마다 센다. 같은 방문자가 같은 게시물을 {@link #WINDOW} 안에 다시 열면 세지 않는다.
 * 봇은 web이 걸러서 부르지 않는다.
 */
@Service
public class PostViews {

  /** 같은 방문자의 다시 보기를 한 번으로 치는 시간. */
  public static final Duration WINDOW = Duration.ofMinutes(10);

  /** 방문자 키: web이 쿠키 값을 SHA-256 16진수로 바꾼 값(64자). 다른 모양도 받되 길이와 글자를 제한한다. */
  private static final Pattern VIEWER_KEY = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");

  private static final Logger log = LoggerFactory.getLogger(PostViews.class);

  private final PostService posts;
  private final PostRepository repository;
  private final Clock clock;

  public PostViews(PostService posts, PostRepository repository, Clock clock) {
    this.posts = posts;
    this.repository = repository;
    this.clock = clock;
  }

  /** 조회 한 번. 볼 수 없는 게시물이면 404. */
  @Transactional
  public ViewCount record(String viewer, String postId, String viewerKey) {
    if (viewerKey == null || !VIEWER_KEY.matcher(viewerKey).matches()) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "방문자 키가 올바르지 않아요.");
    }
    Post post = posts.visible(viewer, postId);
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    boolean counted = repository.markViewed(post.getId(), viewerKey, now, now.minus(WINDOW)) > 0;
    if (counted) {
      repository.addViews(post.getId());
    }
    return new ViewCount(repository.viewCountOf(post.getId()), counted);
  }

  /** 10분이 지나 더는 중복 판단에 쓰지 않는 기록을 지운다. */
  @Scheduled(
      initialDelayString = "${post-views.sweep-initial-delay:PT5M}",
      fixedDelayString = "${post-views.sweep-delay:PT30M}")
  @Transactional
  public void sweep() {
    int removed = repository.forgetViewsBefore(clock.instant().minus(WINDOW));
    if (removed > 0) {
      log.info("조회 기록 {}줄 정리", removed);
    }
  }

  /**
   * @param viewCount 지금 조회수
   * @param counted 이번 조회를 셌는지 (10분 안에 다시 봤으면 false)
   */
  public record ViewCount(long viewCount, boolean counted) {}
}
