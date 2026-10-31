package net.java21.fourcut.content.comment;

import java.util.List;
import java.util.UUID;
import net.java21.fourcut.content.error.ApiException;
import net.java21.fourcut.content.error.ErrorCode;
import net.java21.fourcut.content.post.Post;
import net.java21.fourcut.content.post.PostRepository;
import net.java21.fourcut.content.post.PostService;
import net.java21.fourcut.content.support.Cursor;
import net.java21.fourcut.content.support.CursorPage;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 댓글: 오래된 순 목록, 쓰기, 지우기(댓글 작성자 또는 게시물 작성자). */
@Service
public class CommentService {

  private final CommentRepository comments;
  private final PostRepository posts;
  private final PostService postService;

  public CommentService(CommentRepository comments, PostRepository posts, PostService postService) {
    this.comments = comments;
    this.posts = posts;
    this.postService = postService;
  }

  @Transactional(readOnly = true)
  public CursorPage<CommentView> list(UUID viewer, UUID postId, String cursor, Integer size) {
    postService.visible(viewer, postId);
    int limit = CursorPage.size(size);
    Cursor from = Cursor.decodeOr(cursor, Cursor.OLDEST_FIRST);
    List<Comment> rows = comments.page(postId, from.at(), from.id(), Limit.of(limit + 1));
    return CursorPage.of(
        rows,
        limit,
        c -> new Cursor(c.getCreatedAt(), c.getId()),
        page -> page.stream().map(CommentView::of).toList());
  }

  @Transactional
  public CommentView add(UUID viewer, UUID postId, String text) {
    postService.visible(viewer, postId);
    Comment saved = comments.saveAndFlush(new Comment(postId, viewer, text.strip()));
    posts.addComments(postId, 1);
    return CommentView.of(saved);
  }

  @Transactional
  public void delete(UUID viewer, UUID commentId) {
    Comment comment = comments.findById(commentId).orElseThrow(ApiException::notFound);
    Post post = postService.visible(viewer, comment.getPostId());
    if (!comment.getAuthorId().equals(viewer) && !post.getAuthorId().equals(viewer)) {
      throw new ApiException(ErrorCode.FORBIDDEN);
    }
    comments.delete(comment);
    posts.addComments(post.getId(), -1);
  }
}
