package net.java21.fourcut.content.follow;

import java.time.Instant;

/**
 * 팔로워·팔로잉 목록의 한 줄. 닉네임과 아바타는 web이 auth-service에서 따로 받는다.
 *
 * @param userId 목록에 나온 사용자 ID
 * @param followedAt 팔로우한 시각
 * @param followedByMe 보는 사람이 이 사용자를 팔로우하는지
 */
public record FollowEntry(String userId, Instant followedAt, boolean followedByMe) {}
