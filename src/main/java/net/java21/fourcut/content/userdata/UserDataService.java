package net.java21.fourcut.content.userdata;

import java.util.ArrayList;
import java.util.List;
import net.java21.fourcut.content.comic.Comic;
import net.java21.fourcut.content.comic.ComicRepository;
import net.java21.fourcut.content.comment.Comment;
import net.java21.fourcut.content.comment.CommentRepository;
import net.java21.fourcut.content.follow.FollowRepository;
import net.java21.fourcut.content.post.PostLike;
import net.java21.fourcut.content.post.PostLikeRepository;
import net.java21.fourcut.content.post.PostRepository;
import net.java21.fourcut.content.storage.ObjectStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 탈퇴한 사용자의 콘텐츠를 모두 지운다: 만화(이미지 포함)·게시물·남의 게시물에 단 좋아요·댓글·팔로우(양방향). 여러 번 불러도 결과가 같다. */
@Service
public class UserDataService {

  private final ComicRepository comics;
  private final PostRepository posts;
  private final PostLikeRepository likes;
  private final CommentRepository comments;
  private final FollowRepository follows;
  private final ObjectStorage storage;

  public UserDataService(
      ComicRepository comics,
      PostRepository posts,
      PostLikeRepository likes,
      CommentRepository comments,
      FollowRepository follows,
      ObjectStorage storage) {
    this.comics = comics;
    this.posts = posts;
    this.likes = likes;
    this.comments = comments;
    this.follows = follows;
    this.storage = storage;
  }

  @Transactional
  public void deleteAll(String userId) {
    List<Comic> owned = comics.findByOwnerId(userId);
    List<String> keys = new ArrayList<>();
    for (Comic c : owned) {
      keys.add(c.getImageKey());
      keys.add(c.getThumbKey());
    }
    // 이미지를 먼저 지운다. DB 삭제가 실패해도 다시 부르면 같은 키를 또 지우므로 안전하다
    storage.delete(keys);
    for (PostLike like : likes.findByUserId(userId)) {
      posts.addLikes(like.getPostId(), -1);
      likes.delete(like);
    }
    for (Comment comment : comments.findByAuthorId(userId)) {
      posts.addComments(comment.getPostId(), -1);
      comments.delete(comment);
    }
    comics.deleteAll(owned);
    follows.deleteAllOf(userId);
  }
}
