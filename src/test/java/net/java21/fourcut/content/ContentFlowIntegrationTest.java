package net.java21.fourcut.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.delete;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.put;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import net.java21.fourcut.content.comic.Comic;
import net.java21.fourcut.content.comic.ComicRepository;
import net.java21.fourcut.content.post.PostRepository;
import net.java21.fourcut.content.support.PublicId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.restdocs.headers.HeaderDescriptor;
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** 만화 등록 → 만화함 → 올리기 → 피드 → 좋아요·댓글 → 팔로우 → 내리기·삭제 → 탈퇴 정리. 같은 테스트가 REST Docs 스니펫을 만든다. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(TestcontainersConfiguration.class)
class ContentFlowIntegrationTest {

  private static final HeaderDescriptor USER =
      headerWithName("X-User-Id").description("게이트웨이가 붙인 사용자 ID");

  private static final FieldDescriptor[] ERROR = {
    fieldWithPath("error.code").description("오류 코드"),
    fieldWithPath("error.message").description("사용자에게 보여도 되는 문장"),
    fieldWithPath("error.traceId").description("요청 추적 ID (X-Trace-Id)")
  };

  private static final FieldDescriptor[] COMIC = {
    fieldWithPath("id").description("만화 ID"),
    fieldWithPath("conversationId").description("만화를 만든 대화 ID"),
    fieldWithPath("title").description("콘티 제목"),
    fieldWithPath("language").description("대사 언어 (ko, en)"),
    fieldWithPath("nameWarnings").description("대사에 남은 실명 의심 단어. 올리기 전에 경고로 보여 준다"),
    fieldWithPath("state").description("IN_INBOX(만화함) 또는 POSTED(올림)"),
    fieldWithPath("postId")
        .type(JsonFieldType.STRING)
        .optional()
        .description("올린 게시물 ID. 만화함에만 있으면 null"),
    fieldWithPath("createdAt").description("만든 시각")
  };

  private static final FieldDescriptor[] POST = {
    fieldWithPath("postId").description("게시물 ID"),
    fieldWithPath("comicId").description("만화 ID"),
    fieldWithPath("authorId").description("작성자 ID. 이름·사진은 auth-service `/api/v1/users?ids=`로 받는다"),
    fieldWithPath("title").description("만화 제목"),
    fieldWithPath("caption").type(JsonFieldType.STRING).optional().description("캡션 (0~100자)"),
    fieldWithPath("visibility").description("PUBLIC 또는 FOLLOWERS"),
    fieldWithPath("publishedAt").description("올린 시각 (피드 정렬 기준)"),
    fieldWithPath("likeCount").description("좋아요 수"),
    fieldWithPath("commentCount").description("댓글 수"),
    fieldWithPath("likedByMe").description("내가 좋아요를 눌렀는지")
  };

  private static final FieldDescriptor[] COMMENT = {
    fieldWithPath("id").description("댓글 ID"),
    fieldWithPath("postId").description("게시물 ID"),
    fieldWithPath("authorId").description("작성자 ID"),
    fieldWithPath("text").description("내용 (1~300자)"),
    fieldWithPath("createdAt").description("쓴 시각")
  };

  private static final FieldDescriptor[] IMAGE = {
    fieldWithPath("url").description("사전 서명 GET 주소. web 서버가 받아 이미지를 중계한다 (브라우저에 넘기지 않음)"),
    fieldWithPath("expiresIn").description("유효 시간(초)")
  };

  @Autowired private MockMvc mvc;
  @Autowired private ComicRepository comics;
  @Autowired private PostRepository posts;
  @Autowired private S3Client s3;

  private static RestDocumentationResultHandler doc(String id, Snippet... snippets) {
    return document(
        id, preprocessRequest(prettyPrint()), preprocessResponse(prettyPrint()), snippets);
  }

  private static MockHttpServletRequestBuilder as(
      MockHttpServletRequestBuilder request, String user) {
    return request.header("X-User-Id", user);
  }

  private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request);
  }

  private String register(String owner, String conversation) throws Exception {
    String body =
        perform(
                post("/internal/comics")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody(owner, conversation)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.comicId");
  }

  private static String registerBody(String owner, String conversation) {
    return """
        {"ownerId":"%s","conversationId":"%s","title":"편의점 앞에서","imageKey":"comics/%s/full.png",
         "thumbKey":"comics/%s/thumb.png","language":"ko","nameWarnings":["민지"]}
        """
        .formatted(owner, conversation, conversation, conversation);
  }

  private String publish(String author, String comicId, String visibility) throws Exception {
    String body =
        perform(
                as(post("/api/v1/posts"), author)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"comicId":"%s","caption":"오늘 있었던 일","visibility":"%s","participantsConsented":true}
                        """
                            .formatted(comicId, visibility)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(body, "$.postId");
  }

  private void putObject(String key) {
    s3.putObject(
        b -> b.bucket(TestcontainersConfiguration.BUCKET).key(key), RequestBody.fromString("png"));
  }

  private boolean objectExists(String key) {
    try {
      s3.headObject(b -> b.bucket(TestcontainersConfiguration.BUCKET).key(key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
      if (e.statusCode() == 404) {
        return false;
      }
      throw e;
    }
  }

  @Test
  @DisplayName("FR-001~006: 만화 등록, 만화함, 올리기, 피드, 상세, 이미지")
  void comicToFeed() throws Exception {
    String alice = PublicId.generate();
    String conversation = PublicId.generate();

    String comicId =
        JsonPath.read(
            perform(
                    post("/internal/comics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(alice, conversation)))
                .andExpect(status().isCreated())
                .andDo(
                    doc(
                        "internal-comics",
                        requestFields(
                            fieldWithPath("ownerId").description("만화 주인 (대화를 시작한 사용자)"),
                            fieldWithPath("conversationId")
                                .description("대화 ID. 같은 대화로 다시 오면 기존 ID를 돌려준다(200)"),
                            fieldWithPath("title").description("콘티 제목 (최대 100자)"),
                            fieldWithPath("imageKey").description("원본 이미지 객체 키"),
                            fieldWithPath("thumbKey").description("썸네일 객체 키"),
                            fieldWithPath("language").description("ko 또는 en"),
                            fieldWithPath("nameWarnings").description("실명 의심 단어 목록")),
                        responseFields(fieldWithPath("comicId").description("만화 ID"))))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.comicId");

    // 같은 대화로 다시 등록하면 200 + 같은 ID (멱등)
    perform(
            post("/internal/comics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody(alice, conversation)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.comicId").value(comicId));

    perform(as(get("/api/v1/comics").param("size", "20"), alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].id").value(comicId))
        .andExpect(jsonPath("$.items[0].nameWarnings[0]").value("민지"))
        .andExpect(jsonPath("$.nextCursor").isEmpty())
        .andDo(
            doc(
                "comics-inbox",
                requestHeaders(USER),
                queryParameters(
                    parameterWithName("cursor").optional().description("다음 페이지 커서"),
                    parameterWithName("size").optional().description("페이지 크기 (기본 20, 최대 50)")),
                responseFields(
                        fieldWithPath("nextCursor")
                            .type(JsonFieldType.STRING)
                            .optional()
                            .description("다음 페이지 커서. 마지막이면 null"))
                    .andWithPrefix("items[].", COMIC)));

    perform(as(get("/api/v1/comics/{id}", comicId), alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("IN_INBOX"))
        .andDo(
            doc(
                "comic",
                requestHeaders(USER),
                pathParameters(parameterWithName("id").description("만화 ID")),
                responseFields(COMIC)));

    perform(as(get("/api/v1/comics/{id}/image", comicId).param("size", "thumb"), alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.expiresIn").value(60))
        .andDo(
            doc(
                "comic-image",
                pathParameters(parameterWithName("id").description("만화 ID")),
                queryParameters(
                    parameterWithName("size").optional().description("full(기본) 또는 thumb")),
                responseFields(IMAGE)));

    // 다른 사람의 만화는 없는 것으로 본다
    perform(as(get("/api/v1/comics/{id}", comicId), PublicId.generate()))
        .andExpect(status().isNotFound());

    // 참여자 동의 없이는 올리지 않는다
    perform(
            as(post("/api/v1/posts"), alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"comicId\":\""
                        + comicId
                        + "\",\"visibility\":\"PUBLIC\",\"participantsConsented\":false}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("CONSENT_REQUIRED"))
        .andDo(doc("posts-create-no-consent", responseFields(ERROR)));

    String postId =
        JsonPath.read(
            perform(
                    as(post("/api/v1/posts"), alice)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                            {"comicId":"%s","caption":"오늘 있었던 일","visibility":"PUBLIC","participantsConsented":true}
                            """
                                .formatted(comicId)))
                .andExpect(status().isCreated())
                .andDo(
                    doc(
                        "posts-create",
                        requestHeaders(USER),
                        requestFields(
                            fieldWithPath("comicId").description("만화함의 만화 ID"),
                            fieldWithPath("caption").optional().description("캡션 (0~100자)"),
                            fieldWithPath("visibility")
                                .description("PUBLIC(전체) 또는 FOLLOWERS(팔로워만)"),
                            fieldWithPath("participantsConsented")
                                .description("함께 대화한 사람의 동의. true가 아니면 400 CONSENT_REQUIRED")),
                        responseFields(fieldWithPath("postId").description("게시물 ID"))))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.postId");

    perform(
            as(post("/api/v1/posts"), alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"comicId\":\""
                        + comicId
                        + "\",\"visibility\":\"PUBLIC\",\"participantsConsented\":true}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error.code").value("ALREADY_PUBLISHED"));

    perform(as(get("/api/v1/comics/{id}", comicId), alice))
        .andExpect(jsonPath("$.state").value("POSTED"))
        .andExpect(jsonPath("$.postId").value(postId));

    String bob = PublicId.generate();
    perform(as(get("/api/v1/feed"), bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.postId == '" + postId + "')].title").value("편의점 앞에서"))
        .andDo(
            doc(
                "feed",
                requestHeaders(USER),
                queryParameters(
                    parameterWithName("cursor").optional().description("다음 페이지 커서"),
                    parameterWithName("size").optional().description("페이지 크기 (기본 20, 최대 50)")),
                responseFields(
                        fieldWithPath("nextCursor")
                            .type(JsonFieldType.STRING)
                            .optional()
                            .description("다음 페이지 커서. 마지막이면 null"))
                    .andWithPrefix("items[].", POST)));

    perform(as(get("/api/v1/posts/{id}", postId), bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.authorId").value(alice.toString()))
        .andDo(
            doc(
                "post",
                requestHeaders(USER),
                pathParameters(parameterWithName("id").description("게시물 ID")),
                responseFields(POST)));

    perform(as(get("/api/v1/posts").param("authorId", alice.toString()), bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].postId").value(postId))
        .andDo(
            doc(
                "posts-by-author",
                queryParameters(
                    parameterWithName("authorId").description("프로필 주인 ID"),
                    parameterWithName("cursor").optional().description("다음 페이지 커서"),
                    parameterWithName("size").optional().description("페이지 크기")),
                responseFields(
                    subsectionWithPath("items").description("게시물 목록 (피드와 같은 형식)"),
                    fieldWithPath("nextCursor")
                        .type(JsonFieldType.STRING)
                        .optional()
                        .description("다음 페이지 커서"))));

    perform(as(get("/api/v1/posts/{id}/image", postId).param("size", "full"), bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/4cut/comics/")))
        .andDo(
            doc(
                "post-image",
                pathParameters(parameterWithName("id").description("게시물 ID")),
                queryParameters(
                    parameterWithName("size").optional().description("full(기본) 또는 thumb")),
                responseFields(IMAGE)));

    perform(as(get("/api/v1/posts/{id}/image", postId).param("size", "huge"), bob))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("FR-007~009: 좋아요·댓글 (멱등, 집계, 삭제 권한)")
  void likesAndComments() throws Exception {
    String alice = PublicId.generate();
    String bob = PublicId.generate();
    String carol = PublicId.generate();
    String postId = publish(alice, register(alice, PublicId.generate()), "PUBLIC");

    perform(as(put("/api/v1/posts/{id}/like", postId), bob))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "post-like",
                requestHeaders(USER),
                pathParameters(parameterWithName("id").description("게시물 ID"))));
    perform(as(put("/api/v1/posts/{id}/like", postId), bob)).andExpect(status().isNoContent());
    perform(as(get("/api/v1/posts/{id}", postId), bob))
        .andExpect(jsonPath("$.likeCount").value(1))
        .andExpect(jsonPath("$.likedByMe").value(true));

    perform(as(delete("/api/v1/posts/{id}/like", postId), bob))
        .andExpect(status().isNoContent())
        .andDo(doc("post-unlike", pathParameters(parameterWithName("id").description("게시물 ID"))));
    perform(as(delete("/api/v1/posts/{id}/like", postId), bob)).andExpect(status().isNoContent());
    perform(as(get("/api/v1/posts/{id}", postId), bob)).andExpect(jsonPath("$.likeCount").value(0));

    String commentId =
        JsonPath.read(
            perform(
                    as(post("/api/v1/posts/{postId}/comments", postId), bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"ㅋㅋㅋ 너무 웃겨요\"}"))
                .andExpect(status().isCreated())
                .andDo(
                    doc(
                        "comments-create",
                        requestHeaders(USER),
                        pathParameters(parameterWithName("postId").description("게시물 ID")),
                        requestFields(fieldWithPath("text").description("내용 (1~300자)")),
                        responseFields(COMMENT)))
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    perform(
            as(post("/api/v1/posts/{postId}/comments", postId), carol)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"두 번째\"}"))
        .andExpect(status().isCreated());
    perform(
            as(post("/api/v1/posts/{postId}/comments", postId), carol)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\" \"}"))
        .andExpect(status().isBadRequest());

    String firstPage =
        perform(as(get("/api/v1/posts/{postId}/comments", postId).param("size", "1"), alice))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].id").value(commentId))
            .andDo(
                doc(
                    "comments",
                    pathParameters(parameterWithName("postId").description("게시물 ID")),
                    queryParameters(
                        parameterWithName("cursor").optional().description("다음 페이지 커서"),
                        parameterWithName("size").optional().description("페이지 크기")),
                    responseFields(
                            fieldWithPath("nextCursor")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("다음 페이지 커서"))
                        .andWithPrefix("items[].", COMMENT)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String next = JsonPath.read(firstPage, "$.nextCursor");
    perform(as(get("/api/v1/posts/{postId}/comments", postId).param("cursor", next), alice))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].text").value("두 번째"))
        .andExpect(jsonPath("$.nextCursor").isEmpty());

    perform(as(get("/api/v1/posts/{id}", postId), alice))
        .andExpect(jsonPath("$.commentCount").value(2));

    // 다른 사람은 지울 수 없다
    perform(as(delete("/api/v1/comments/{id}", commentId), carol))
        .andExpect(status().isForbidden());
    // 게시물 작성자는 지울 수 있다
    perform(as(delete("/api/v1/comments/{id}", commentId), alice))
        .andExpect(status().isNoContent())
        .andDo(
            doc("comments-delete", pathParameters(parameterWithName("id").description("댓글 ID"))));
    perform(as(delete("/api/v1/comments/{id}", commentId), alice)).andExpect(status().isNotFound());
    perform(as(get("/api/v1/posts/{id}", postId), alice))
        .andExpect(jsonPath("$.commentCount").value(1));

    perform(as(get("/api/v1/feed").param("cursor", "not-a-cursor"), alice))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    perform(as(get("/api/v1/feed").param("size", "51"), alice)).andExpect(status().isBadRequest());
    perform(get("/api/v1/feed")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("FR-010~011: 팔로우, 팔로워 공개 게시물")
  void followsAndVisibility() throws Exception {
    String alice = PublicId.generate();
    String bob = PublicId.generate();
    String postId = publish(alice, register(alice, PublicId.generate()), "FOLLOWERS");

    perform(as(get("/api/v1/posts/{id}", postId), bob)).andExpect(status().isNotFound());
    perform(as(put("/api/v1/posts/{id}/like", postId), bob)).andExpect(status().isNotFound());
    perform(as(get("/api/v1/posts").param("authorId", alice.toString()), bob))
        .andExpect(jsonPath("$.items.length()").value(0));

    perform(as(put("/api/v1/follows/{userId}", alice), bob))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "follow",
                requestHeaders(USER),
                pathParameters(parameterWithName("userId").description("팔로우할 사용자 ID"))));
    perform(as(put("/api/v1/follows/{userId}", alice), bob)).andExpect(status().isNoContent());

    perform(as(get("/api/v1/posts/{id}", postId), bob)).andExpect(status().isOk());
    perform(as(get("/api/v1/follows/{userId}/stats", alice), bob))
        .andExpect(jsonPath("$.followers").value(1))
        .andExpect(jsonPath("$.following").value(0))
        .andExpect(jsonPath("$.followedByMe").value(true))
        .andDo(
            doc(
                "follow-stats",
                pathParameters(parameterWithName("userId").description("사용자 ID")),
                responseFields(
                    fieldWithPath("followers").description("팔로워 수"),
                    fieldWithPath("following").description("팔로잉 수"),
                    fieldWithPath("followedByMe").description("내가 팔로우하는지"))));

    perform(as(put("/api/v1/follows/{userId}", bob), bob))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("SELF_FOLLOW"));

    perform(as(delete("/api/v1/follows/{userId}", alice), bob))
        .andExpect(status().isNoContent())
        .andDo(doc("unfollow", pathParameters(parameterWithName("userId").description("사용자 ID"))));
    perform(as(get("/api/v1/posts/{id}", postId), bob)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("FR-012~013: 내리기는 만화함으로, 삭제는 만화·이미지까지")
  void unpublishAndDelete() throws Exception {
    String alice = PublicId.generate();
    String conversation = PublicId.generate();
    String comicId = register(alice, conversation);
    String postId = publish(alice, comicId, "PUBLIC");

    perform(as(delete("/api/v1/posts/{id}?mode=unpublish", postId), PublicId.generate()))
        .andExpect(status().isForbidden());
    perform(as(delete("/api/v1/posts/{id}?mode=nope", postId), alice))
        .andExpect(status().isBadRequest());

    perform(as(delete("/api/v1/posts/{id}?mode=unpublish", postId), alice))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "posts-delete",
                pathParameters(parameterWithName("id").description("게시물 ID")),
                queryParameters(
                    parameterWithName("mode")
                        .description("unpublish(만화함으로 내리기) 또는 delete(만화까지 삭제)"))));
    assertThat(comics.findByPublicId(comicId))
        .get()
        .extracting(Comic::getState)
        .isEqualTo(Comic.State.IN_INBOX);
    assertThat(posts.findByPublicId(postId)).isEmpty();

    String again = publish(alice, comicId, "PUBLIC");
    putObject("comics/" + conversation + "/full.png");
    putObject("comics/" + conversation + "/thumb.png");
    perform(as(delete("/api/v1/posts/{id}?mode=delete", again), alice))
        .andExpect(status().isNoContent());
    assertThat(comics.findByPublicId(comicId)).isEmpty();
    assertThat(objectExists("comics/" + conversation + "/full.png")).isFalse();

    String other = register(alice, PublicId.generate());
    perform(as(delete("/api/v1/comics/{id}", other), alice))
        .andExpect(status().isNoContent())
        .andDo(doc("comics-delete", pathParameters(parameterWithName("id").description("만화 ID"))));
    perform(as(delete("/api/v1/comics/{id}", other), alice)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("M-03: 탈퇴하면 만화·게시물·반응·팔로우·이미지를 모두 지운다 (멱등)")
  void withdrawal() throws Exception {
    String alice = PublicId.generate();
    String bob = PublicId.generate();
    String conversation = PublicId.generate();
    String alicePost = publish(alice, register(alice, conversation), "PUBLIC");
    String bobPost = publish(bob, register(bob, PublicId.generate()), "PUBLIC");
    putObject("comics/" + conversation + "/full.png");
    perform(as(put("/api/v1/posts/{id}/like", bobPost), alice));
    perform(
        as(post("/api/v1/posts/{postId}/comments", bobPost), alice)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"text\":\"안녕\"}"));
    perform(as(put("/api/v1/follows/{userId}", bob), alice));
    perform(as(put("/api/v1/follows/{userId}", alice), bob));

    perform(delete("/internal/users/{userId}/data", alice))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "internal-user-data-delete",
                pathParameters(parameterWithName("userId").description("탈퇴한 사용자 ID"))));
    perform(delete("/internal/users/{userId}/data", alice)).andExpect(status().isNoContent());

    assertThat(posts.findByPublicId(alicePost)).isEmpty();
    assertThat(comics.findByOwnerId(alice)).isEmpty();
    assertThat(objectExists("comics/" + conversation + "/full.png")).isFalse();
    perform(as(get("/api/v1/posts/{id}", bobPost), bob))
        .andExpect(jsonPath("$.likeCount").value(0))
        .andExpect(jsonPath("$.commentCount").value(0));
    perform(as(get("/api/v1/follows/{userId}/stats", bob), bob))
        .andExpect(jsonPath("$.followers").value(0))
        .andExpect(jsonPath("$.following").value(0));
  }

  @Test
  @DisplayName("내부 등록 본문이 틀리면 400")
  void invalidRegistration() throws Exception {
    perform(
            post("/internal/comics")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ownerId\":\"x\"}"))
        .andExpect(status().isBadRequest());
    perform(
            post("/internal/comics")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    registerBody(PublicId.generate(), PublicId.generate())
                        .replace("\"ko\"", "\"jp\"")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
  }
}
