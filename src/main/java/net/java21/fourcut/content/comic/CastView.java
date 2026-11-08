package net.java21.fourcut.content.comic;

/**
 * 밖으로 보이는 등장인물 한 명.
 *
 * @param role 만화 속 호칭 (할머니, 손녀…)
 * @param name 캐릭터 이름 (캐릭터 DB에서 찾지 못했으면 가명)
 * @param lookId 캐릭터 모습 ID (그림은 {@code /api/v1/characters/looks/{lookId}/image}). 없으면 null
 * @param portrait lookId가 없을 때 만화 컷에서 잘라 낸 얼굴 그림이 있는지. 그림은 게시물·만화 이미지 주소에 {@code cast=<순서>}를 붙여 받는다
 */
public record CastView(String role, String name, String lookId, boolean portrait) {

  static CastView of(CastMember m) {
    return new CastView(
        m.role(), m.name(), m.lookId(), m.lookId() == null && m.portraitKey() != null);
  }
}
