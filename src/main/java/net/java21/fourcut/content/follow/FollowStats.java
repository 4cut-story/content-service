package net.java21.fourcut.content.follow;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 프로필 숫자.
 *
 * @param followedByMe 보는 사람이 이 사용자를 팔로우하는지
 * @param posts 보는 사람이 볼 수 있는 이 사용자의 게시물 수
 * @param likes 그 게시물들이 받은 좋아요 합계
 * @param comics 만든 4컷 전체 수. 본인이 볼 때만 온다
 */
public record FollowStats(
    long followers,
    long following,
    boolean followedByMe,
    long posts,
    long likes,
    @JsonInclude(JsonInclude.Include.NON_NULL) Long comics) {}
