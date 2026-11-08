package net.java21.fourcut.content.comic;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 그림으로 만화 등록(파이프라인 밖에서 그린 시험 만화)을 쓸 수 있는 사용자. 아무나 그림을 올려 피드에 내지 못하게 비워 두면 아무도 못 쓴다.
 *
 * @param users 허용할 사용자 ID 목록 (환경 변수 COMIC_IMPORT_USERS, 쉼표로 구분)
 */
@ConfigurationProperties("comic-import")
public record ImportProperties(@DefaultValue List<String> users) {}
