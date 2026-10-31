package net.java21.fourcut.content.storage;

import java.time.Duration;
import java.util.Collection;

/** 객체 저장소. 키는 버킷 안의 경로(예: {@code comics/<id>/full.png})다. */
public interface ObjectStorage {

  /** 짧게 쓰는 사전 서명 GET 주소. web 서버만 받아서 이미지를 중계한다. */
  String presignGet(String key, Duration ttl);

  /** 객체를 지운다. 없는 키도 성공이다. */
  void delete(Collection<String> keys);
}
