package net.java21.fourcut.content.comic;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;
import net.java21.fourcut.content.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 목록 썸네일(첫 컷 JPEG)을 만든다. 목록 화면은 4컷 전체가 아니라 첫 컷만 보여 주므로, 원본에서 첫 컷만 잘라 작게 저장해 둔다.
 *
 * <p>처음 요청할 때 만들고, 주기 작업이 아직 없는 만화(새 만화, 예전 만화)를 미리 채운다. 원본을 읽을 수 없는 그림이면 기존 썸네일 키를 남겨 다시 시도하지 않는다.
 * 저장소 오류처럼 잠깐의 실패는 남기지 않고 다음에 다시 시도한다.
 */
@Component
public class ComicCovers {

  private static final Logger log = LoggerFactory.getLogger(ComicCovers.class);

  /** 주기 작업 한 번에 채우는 만화 수. */
  static final int SWEEP_BATCH = 50;

  private final ComicRepository comics;
  private final ObjectStorage storage;
  private final TransactionTemplate separate;

  public ComicCovers(
      ComicRepository comics, ObjectStorage storage, PlatformTransactionManager transactions) {
    this.comics = comics;
    this.storage = storage;
    this.separate = new TransactionTemplate(transactions);
    this.separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** 목록 썸네일 키. 아직 없으면 지금 만든다. 만들 수 없으면 기존 썸네일 키. */
  public String keyOf(Comic comic) {
    if (comic.getCoverKey() != null) {
      return comic.getCoverKey();
    }
    String key = make(comic);
    return key == null ? comic.getThumbKey() : key;
  }

  /** 목록 썸네일이 없는 만화를 채운다. 새 만화는 몇 분 안에, 예전 만화는 배치마다 채워진다. */
  @Scheduled(
      initialDelayString = "${comic-cover.sweep-initial-delay:PT1M}",
      fixedDelayString = "${comic-cover.sweep-delay:PT2M}")
  public void sweep() {
    List<Comic> rows = comics.withoutCover(Limit.of(SWEEP_BATCH));
    int made = 0;
    for (Comic comic : rows) {
      if (make(comic) != null) {
        made++;
      }
    }
    if (!rows.isEmpty()) {
      log.info("목록 썸네일 {}/{}개 만듦", made, rows.size());
    }
  }

  /** 첫 컷 썸네일을 만들어 올리고 키를 남긴다. 잠깐의 실패면 null. */
  String make(Comic comic) {
    byte[] original;
    try {
      original = storage.get(comic.getImageKey());
    } catch (RuntimeException e) {
      log.warn("목록 썸네일: 원본을 받지 못함 comic={}", comic.getPublicId(), e);
      return null;
    }
    BufferedImage image = decode(original);
    if (image == null) {
      // 읽을 수 없는 그림은 다시 시도해도 같으니 기존 썸네일을 쓴다
      record(comic, comic.getThumbKey());
      return comic.getThumbKey();
    }
    String key = LivePlayback.folder(comic.getImageKey()) + "cover.jpg";
    try {
      storage.put(key, ComicImages.cover(image), "image/jpeg");
    } catch (RuntimeException e) {
      log.warn("목록 썸네일: 올리지 못함 comic={}", comic.getPublicId(), e);
      return null;
    }
    record(comic, key);
    return key;
  }

  private void record(Comic comic, String key) {
    separate.executeWithoutResult(s -> comics.recordCover(comic.getId(), key));
  }

  private static BufferedImage decode(byte[] bytes) {
    try {
      return ImageIO.read(new ByteArrayInputStream(bytes));
    } catch (IOException e) {
      return null;
    }
  }
}
