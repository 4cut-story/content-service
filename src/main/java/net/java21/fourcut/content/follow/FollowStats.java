package net.java21.fourcut.content.follow;

/**
 * 프로필 팔로우 숫자.
 *
 * @param followedByMe 보는 사람이 이 사용자를 팔로우하는지
 */
public record FollowStats(long followers, long following, boolean followedByMe) {}
