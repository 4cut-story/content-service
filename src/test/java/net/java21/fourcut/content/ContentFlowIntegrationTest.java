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
@SpringBootTest(properties = "comic-import.users=" + ContentFlowIntegrationTest.IMPORTER)
@AutoConfigureMockMvc
@AutoConfigureRestDocs
@Import(TestcontainersConfiguration.class)
class ContentFlowIntegrationTest {

  /** 그림으로 만화 등록을 허용한 사용자. */
  static final String IMPORTER = "importer_aaaaaaaaaaaa";

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
    fieldWithPath("createdAt").description("만든 시각"),
    subsectionWithPath("cast")
        .description(
            "만화 아래 등장인물 소개: role(호칭), name(캐릭터 이름), lookId(캐릭터 모습 ID, 없으면 null). 예전 만화는 빈 목록"),
    fieldWithPath("concept")
        .type(JsonFieldType.STRING)
        .optional()
        .description("그린 그림체 (DAILY, WATERCOLOR…). 예전 만화는 null"),
    fieldWithPath("dialect")
        .type(JsonFieldType.STRING)
        .optional()
        .description("대사 사투리 (NONE, GYEONGSANG…). 예전 만화는 null"),
    subsectionWithPath("production")
        .type(JsonFieldType.OBJECT)
        .optional()
        .description("제작 정보: 단계별 모델·조건·걸린 시간 (conversation-service가 만든 그대로). 예전 만화는 null"),
    fieldWithPath("live").description("라이브 모드 재생 정보가 있는지. true면 /live로 받아 라이브 모드를 연다. 예전 만화는 false")
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
    fieldWithPath("likedByMe").description("내가 좋아요를 눌렀는지"),
    subsectionWithPath("cast")
        .description(
            "만화 아래 등장인물 소개: role(호칭), name(캐릭터 이름), lookId(캐릭터 모습 ID, 없으면 null). 예전 만화는 빈 목록"),
    fieldWithPath("concept")
        .type(JsonFieldType.STRING)
        .optional()
        .description("그린 그림체 (DAILY, WATERCOLOR…). 예전 만화는 null"),
    fieldWithPath("dialect")
        .type(JsonFieldType.STRING)
        .optional()
        .description("대사 사투리 (NONE, GYEONGSANG…). 예전 만화는 null"),
    subsectionWithPath("production")
        .type(JsonFieldType.OBJECT)
        .optional()
        .description("제작 정보: 단계별 모델·조건·걸린 시간 (conversation-service가 만든 그대로). 예전 만화는 null"),
    fieldWithPath("live").description("라이브 모드 재생 정보가 있는지. true면 /live로 받아 라이브 모드를 연다. 예전 만화는 false")
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
         "thumbKey":"comics/%s/thumb.png","language":"ko","nameWarnings":["민지"],
         "cast":[{"role":"할머니","name":"하늘","lookId":"aaaaaaaaaaaaaaaaaaaaa"},{"role":"손녀","name":"도윤","lookId":null,"portraitKey":"comics/%s/cast-1.png"}],
         "concept":"WATERCOLOR","dialect":"GYEONGSANG",
         "production":{"version":1,"totalMs":95000,"steps":[{"step":"DRAW","ms":41000}]}}
        """
        .formatted(owner, conversation, conversation, conversation, conversation);
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
                            fieldWithPath("nameWarnings").description("실명 의심 단어 목록"),
                            fieldWithPath("cast[].role").description("등장인물 호칭 (할머니, 손녀…)"),
                            fieldWithPath("cast[].name").description("캐릭터 이름"),
                            fieldWithPath("cast[].lookId")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description("캐릭터 모습 ID. 캐릭터를 못 찾았으면 null"),
                            fieldWithPath("cast[].portraitKey")
                                .type(JsonFieldType.STRING)
                                .optional()
                                .description(
                                    "캐릭터를 못 찾은 인물의 얼굴 그림 객체 키 (만화 컷에서 잘라 낸 것, comics/<대화 ID>/cast-<순서>.png)."
                                        + " 없으면 null"),
                            fieldWithPath("concept").description("그린 그림체 (DAILY, WATERCOLOR…)"),
                            fieldWithPath("dialect").description("대사 사투리 (NONE, GYEONGSANG…)"),
                            subsectionWithPath("production")
                                .type(JsonFieldType.OBJECT)
                                .optional()
                                .description("제작 정보 (JSON 객체): 단계별 모델·조건·걸린 시간. 내용은 보지 않고 그대로 둔다"),
                            subsectionWithPath("live")
                                .type(JsonFieldType.OBJECT)
                                .optional()
                                .description(
                                    "라이브 모드 재생 정보 (LivePlayback). 그림 경로가 규칙에 맞지 않으면 버리고 등록한다")),
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
        .andExpect(jsonPath("$.cast[0].role").value("할머니"))
        .andExpect(jsonPath("$.cast[0].lookId").value("aaaaaaaaaaaaaaaaaaaaa"))
        .andExpect(jsonPath("$.cast[1].name").value("도윤"))
        .andExpect(jsonPath("$.cast[0].portrait").value(false))
        .andExpect(jsonPath("$.cast[1].portrait").value(true))
        .andExpect(jsonPath("$.cast[1].portraitKey").doesNotExist())
        .andExpect(jsonPath("$.concept").value("WATERCOLOR"))
        .andExpect(jsonPath("$.dialect").value("GYEONGSANG"))
        .andExpect(jsonPath("$.production.steps[0].step").value("DRAW"))
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

    // 캐릭터를 못 찾은 인물은 만화 컷에서 잘라 낸 얼굴 그림을 같은 주소에 cast=<순서>로 받는다
    perform(as(get("/api/v1/posts/{id}/image", postId).param("cast", "1"), bob))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/cast-1.png")))
        .andDo(
            doc(
                "post-cast-image",
                pathParameters(parameterWithName("id").description("게시물 ID")),
                queryParameters(
                    parameterWithName("cast")
                        .description("등장인물 순서(0부터). 그 인물의 얼굴 그림 (cast[].portrait가 true일 때만 있다)")),
                responseFields(IMAGE)));
    perform(as(get("/api/v1/comics/{id}/image", comicId).param("cast", "1"), alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/cast-1.png")));
    // 캐릭터 그림이 있는 인물·없는 순서는 404
    perform(as(get("/api/v1/posts/{id}/image", postId).param("cast", "0"), bob))
        .andExpect(status().isNotFound());
    perform(as(get("/api/v1/posts/{id}/image", postId).param("cast", "5"), bob))
        .andExpect(status().isNotFound());
    perform(as(get("/api/v1/comics/{id}/image", comicId).param("cast", "-1"), alice))
        .andExpect(status().isNotFound());
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
    // 읽기는 로그인 없이 되지만(공개만), 쓰기는 사용자 헤더가 없으면 401
    perform(put("/api/v1/posts/{id}/like", postId)).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("사용자 페이지·포털: 로그인하지 않으면 전체 공개 게시물만 보이고, 작성자는 모두 본다")
  void anonymousSeesOnlyPublicPosts() throws Exception {
    String alice = PublicId.generate();
    String publicConversation = PublicId.generate();
    String publicPost = publish(alice, register(alice, publicConversation), "PUBLIC");
    String followersPost = publish(alice, register(alice, PublicId.generate()), "FOLLOWERS");

    perform(get("/api/v1/posts").param("authorId", alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].postId").value(publicPost))
        .andExpect(jsonPath("$.items[0].likedByMe").value(false));
    perform(as(get("/api/v1/posts").param("authorId", alice), alice))
        .andExpect(jsonPath("$.items.length()").value(2));

    perform(get("/api/v1/feed").param("size", "50"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[?(@.postId == '%s')]".formatted(publicPost)).exists())
        .andExpect(jsonPath("$.items[?(@.postId == '%s')]".formatted(followersPost)).doesNotExist())
        .andExpect(jsonPath("$.items[?(@.visibility == 'FOLLOWERS')]").doesNotExist());

    perform(get("/api/v1/posts/{id}", publicPost)).andExpect(status().isOk());
    perform(get("/api/v1/posts/{id}", followersPost)).andExpect(status().isNotFound());
    perform(get("/api/v1/posts/{id}/image", publicPost).param("size", "thumb"))
        .andExpect(status().isOk());
    perform(get("/api/v1/posts/{id}/image", followersPost)).andExpect(status().isNotFound());
    perform(get("/api/v1/posts/{id}/comments", publicPost)).andExpect(status().isOk());
    perform(get("/api/v1/posts/{id}/comments", followersPost)).andExpect(status().isNotFound());
    perform(get("/api/v1/follows/{userId}/stats", alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.followedByMe").value(false))
        .andExpect(jsonPath("$.posts").value(1));
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
        .andExpect(jsonPath("$.posts").value(1))
        .andExpect(jsonPath("$.likes").value(0))
        .andExpect(jsonPath("$.comics").doesNotExist())
        .andDo(
            doc(
                "follow-stats",
                pathParameters(parameterWithName("userId").description("사용자 ID")),
                responseFields(
                    fieldWithPath("followers").description("팔로워 수"),
                    fieldWithPath("following").description("팔로잉 수"),
                    fieldWithPath("followedByMe").description("내가 팔로우하는지"),
                    fieldWithPath("posts").description("내가 볼 수 있는 게시물 수"),
                    fieldWithPath("likes").description("그 게시물들이 받은 좋아요 합계"),
                    fieldWithPath("comics")
                        .description("만든 4컷 전체 수. 본인이 볼 때만 온다")
                        .optional()
                        .type(JsonFieldType.NUMBER))));
    perform(as(get("/api/v1/follows/{userId}/stats", alice), alice))
        .andExpect(jsonPath("$.posts").value(1))
        .andExpect(jsonPath("$.comics").value(1));

    perform(as(put("/api/v1/follows/{userId}", bob), bob))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("SELF_FOLLOW"));

    perform(as(delete("/api/v1/follows/{userId}", alice), bob))
        .andExpect(status().isNoContent())
        .andDo(doc("unfollow", pathParameters(parameterWithName("userId").description("사용자 ID"))));
    perform(as(get("/api/v1/posts/{id}", postId), bob)).andExpect(status().isNotFound());
  }

  private static final String LIVE =
      """
      {"version":1,"panels":[
       {"index":1,"role":"기","image":"panels/1.png","durationMs":4000,
        "sound":{"publicId":"aaaaaaaaaaaaaaaaaaaaa","title":"밤 풀벌레","volume":0.8,"fadeMs":800,"loop":true,
                 "credit":{"author":"작가","license":"CC-BY-4.0","licenseUrl":"https://l","sourceUrl":"https://s","originalTitle":null}},
        "motion":{"type":"twinkle","frames":["panels/1.png","panels/1-b.png"],"intervalMs":700,"easing":"ease-in-out"}},
       {"index":2,"role":"결","image":"panels/2.png","durationMs":3500,"sound":null,"motion":null}]}
      """;

  private String registerLive(String owner, String conversation, String live) throws Exception {
    String body = registerBody(owner, conversation).strip();
    body = body.substring(0, body.length() - 1) + ",\"live\":" + live + "}";
    String response =
        perform(post("/internal/comics").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.comicId");
  }

  @Test
  @DisplayName("003 FR-001·FR-018·FR-019: 라이브 재생 정보는 만화와 같은 공개 범위로 내주고, 컷 그림 주소를 주고, 삭제 때 함께 지운다")
  void live() throws Exception {
    String alice = PublicId.generate();
    String bob = PublicId.generate();
    String conversation = PublicId.generate();
    String comicId = registerLive(alice, conversation, LIVE);

    perform(as(get("/api/v1/comics/{id}", comicId), alice))
        .andExpect(jsonPath("$.live").value(true));
    perform(as(get("/api/v1/comics/{id}/live", comicId), alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.panels[0].motion.type").value("twinkle"))
        .andExpect(jsonPath("$.panels[0].sound.credit.license").value("CC-BY-4.0"))
        .andDo(
            doc(
                "comic-live",
                pathParameters(parameterWithName("id").description("만화 ID")),
                responseFields(
                    fieldWithPath("version").description("형식 버전 (1)"),
                    subsectionWithPath("panels")
                        .description(
                            "보여 줄 순서대로의 컷: index, role, image, durationMs, sound(배경음·저작자 표시, 없으면 null),"
                                + " motion(두 장 움직임, 없으면 null). common contracts/live-mode.yaml"))));
    perform(as(get("/api/v1/comics/{id}/live", comicId), bob)).andExpect(status().isNotFound());

    perform(
            as(
                get("/api/v1/comics/{id}/image", comicId).param("panel", "1").param("frame", "b"),
                alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/panels/1-b.png")))
        .andDo(
            doc(
                "comic-live-image",
                pathParameters(parameterWithName("id").description("만화 ID")),
                queryParameters(
                    parameterWithName("panel").description("컷 순서(1부터). 말풍선이 들어간 컷 그림"),
                    parameterWithName("frame")
                        .optional()
                        .description("a(첫 그림, 기본) 또는 b(움직임 컷의 두 번째 그림)")),
                responseFields(IMAGE)));
    perform(
            as(
                get("/api/v1/comics/{id}/image", comicId).param("panel", "2").param("frame", "b"),
                alice))
        .andExpect(status().isNotFound());
    perform(as(get("/api/v1/comics/{id}/image", comicId).param("panel", "11"), alice))
        .andExpect(status().isNotFound());
    perform(
            as(
                get("/api/v1/comics/{id}/image", comicId).param("panel", "1").param("frame", "c"),
                alice))
        .andExpect(status().isBadRequest());
    perform(
            as(
                get("/api/v1/comics/{id}/image", comicId).param("panel", "1").param("cast", "0"),
                alice))
        .andExpect(status().isBadRequest());

    String postId = publish(alice, comicId, "FOLLOWERS");
    perform(as(get("/api/v1/posts/{id}", postId), alice)).andExpect(jsonPath("$.live").value(true));
    perform(as(get("/api/v1/posts/{id}/live", postId), alice))
        .andExpect(status().isOk())
        .andDo(doc("post-live", pathParameters(parameterWithName("id").description("게시물 ID"))));
    perform(as(get("/api/v1/posts/{id}/image", postId).param("panel", "2"), alice))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/panels/2.png")));
    // 팔로워만 게시물은 팔로워가 아니면 라이브 정보도 볼 수 없다
    perform(as(get("/api/v1/posts/{id}/live", postId), bob)).andExpect(status().isNotFound());
    perform(as(get("/api/v1/posts/{id}/image", postId).param("panel", "1"), bob))
        .andExpect(status().isNotFound());

    putObject("comics/" + conversation + "/panels/1.png");
    putObject("comics/" + conversation + "/panels/1-b.png");
    putObject("comics/" + conversation + "/live.json");
    perform(as(delete("/api/v1/comics/{id}", comicId), alice)).andExpect(status().isNoContent());
    assertThat(objectExists("comics/" + conversation + "/panels/1-b.png")).isFalse();
    assertThat(objectExists("comics/" + conversation + "/live.json")).isFalse();
  }

  @Test
  @DisplayName("003 FR-002: 예전 만화와 그림 경로가 틀린 재생 정보는 라이브 없이 등록되고 /live는 404다")
  void noLive() throws Exception {
    String alice = PublicId.generate();
    String old = register(alice, PublicId.generate());
    perform(as(get("/api/v1/comics/{id}", old), alice)).andExpect(jsonPath("$.live").value(false));
    perform(as(get("/api/v1/comics/{id}/live", old), alice)).andExpect(status().isNotFound());
    perform(as(get("/api/v1/comics/{id}/image", old).param("panel", "1"), alice))
        .andExpect(status().isNotFound());

    String bad =
        registerLive(
            alice,
            PublicId.generate(),
            "{\"version\":1,\"panels\":[{\"index\":1,\"image\":\"../../secret.png\"}]}");
    perform(as(get("/api/v1/comics/{id}/live", bad), alice)).andExpect(status().isNotFound());
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
    putObject("comics/" + conversation + "/cast-1.png");
    perform(as(delete("/api/v1/posts/{id}?mode=delete", again), alice))
        .andExpect(status().isNoContent());
    assertThat(comics.findByPublicId(comicId)).isEmpty();
    assertThat(objectExists("comics/" + conversation + "/full.png")).isFalse();
    assertThat(objectExists("comics/" + conversation + "/cast-1.png")).isFalse();

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
    putObject("comics/" + conversation + "/cast-1.png");
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
    assertThat(objectExists("comics/" + conversation + "/cast-1.png")).isFalse();
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

  @Test
  @DisplayName("FR-006: 대화로 찾은 만화에 제작 정보를 채운다. 있으면 그대로 두고, overwrite면 바꾼다")
  void production() throws Exception {
    String alice = PublicId.generate();
    String conversation = PublicId.generate();
    String comicId = register(alice, conversation);

    // 이미 있으면 그대로 둔다
    perform(
            put("/internal/comics/by-conversation/{conversationId}/production", conversation)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":1,\"totalMs\":1}"))
        .andExpect(status().isNoContent());
    perform(as(get("/api/v1/comics/{id}", comicId), alice))
        .andExpect(jsonPath("$.production.totalMs").value(95000));

    perform(
            put("/internal/comics/by-conversation/{conversationId}/production", conversation)
                .param("overwrite", "true")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"totalMs":120000,"steps":[{"step":"TRANSCRIBE","ms":30000}]}
                    """))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "internal-comics-production",
                pathParameters(parameterWithName("conversationId").description("대화 ID")),
                queryParameters(
                    parameterWithName("overwrite")
                        .optional()
                        .description("true면 이미 있는 제작 정보도 바꾼다. 기본은 비어 있을 때만 채운다"))));
    perform(as(get("/api/v1/comics/{id}", comicId), alice))
        .andExpect(jsonPath("$.production.totalMs").value(120000));

    // 주인은 만화 ID로 제작 정보를 바꾼다 (파이프라인 밖에서 그린 시험 만화)
    perform(
            as(put("/api/v1/comics/{id}/production", comicId), alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"version":1,"totalMs":7000,"steps":[{"step":"DRAW","ms":7000,"attempts":1,"jobs":[]}]}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.production.totalMs").value(7000))
        .andDo(
            doc(
                "comics-production",
                requestHeaders(USER),
                pathParameters(parameterWithName("id").description("만화 ID")),
                responseFields(COMIC)));
    perform(
            as(put("/api/v1/comics/{id}/production", comicId), PublicId.generate())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
    perform(
            as(put("/api/v1/comics/{id}/production", comicId), alice)
                .contentType(MediaType.APPLICATION_JSON)
                .content("\"x\""))
        .andExpect(status().isBadRequest());

    // 객체가 아니면 400, 없는 대화면 404
    perform(
            put("/internal/comics/by-conversation/{conversationId}/production", conversation)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[1]"))
        .andExpect(status().isBadRequest());
    perform(
            put("/internal/comics/by-conversation/{conversationId}/production", PublicId.generate())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNotFound());
  }

  private static String png(int width, int height) throws Exception {
    var image =
        new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var out = new java.io.ByteArrayOutputStream();
    javax.imageio.ImageIO.write(image, "png", out);
    return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
  }

  private static String importBody(String image) {
    return """
        {"title":"[회차 29 · dev NF4] 비 오는 교문","image":"%s",
         "cast":[{"role":"남자","name":"하늘","lookId":null,"portraitKey":"comics/other/cast-0.png"}],
         "production":{"version":1,"round":29,"variant":"dev-nf4","totalMs":210000}}
        """
        .formatted(image);
  }

  @Test
  @DisplayName("그림으로 만화 등록: 허용한 사용자의 만화함에 들어가고 썸네일을 만들며, 올리면 피드에 나온다")
  void importComic() throws Exception {
    String body =
        perform(
                as(post("/api/v1/comics"), IMPORTER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(importBody(png(800, 1600))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("[회차 29 · dev NF4] 비 오는 교문"))
            .andExpect(jsonPath("$.language").value("ko"))
            .andExpect(jsonPath("$.state").value("IN_INBOX"))
            .andExpect(jsonPath("$.cast[0].name").value("하늘"))
            // 사용자가 보낸 얼굴 그림 키는 받지 않는다
            .andExpect(jsonPath("$.cast[0].portrait").value(false))
            .andExpect(jsonPath("$.production.variant").value("dev-nf4"))
            .andDo(
                doc(
                    "comics-import",
                    requestHeaders(USER),
                    requestFields(
                        fieldWithPath("title").description("만화 제목"),
                        fieldWithPath("image").description("완성 만화 그림 (PNG 또는 JPEG, Base64)"),
                        subsectionWithPath("cast").optional().description("등장인물 소개"),
                        subsectionWithPath("production")
                            .optional()
                            .description("제작 정보 (version 1 형식)")),
                    responseFields(COMIC)))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String comicId = JsonPath.read(body, "$.id");
    Comic comic = comics.findByPublicId(comicId).orElseThrow();
    assertThat(comic.getImageKey()).endsWith("/full.png");
    assertThat(objectExists(comic.getImageKey())).isTrue();
    assertThat(objectExists(comic.getThumbKey())).isTrue();
    var thumb =
        javax.imageio.ImageIO.read(
            s3.getObject(
                b -> b.bucket(TestcontainersConfiguration.BUCKET).key(comic.getThumbKey())));
    assertThat(thumb.getWidth()).isEqualTo(400);
    assertThat(thumb.getHeight()).isEqualTo(800);
    publish(IMPORTER, comicId, "PUBLIC");

    // 허용하지 않은 사용자는 403, PNG·JPEG가 아니면 415, Base64가 틀리면 400
    perform(
            as(post("/api/v1/comics"), PublicId.generate())
                .contentType(MediaType.APPLICATION_JSON)
                .content(importBody(png(10, 10))))
        .andExpect(status().isForbidden());
    perform(
            as(post("/api/v1/comics"), IMPORTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(importBody("R0lGODlhAQABAAAAACw=")))
        .andExpect(status().isUnsupportedMediaType());
    perform(
            as(post("/api/v1/comics"), IMPORTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(importBody("iVBORw0KGgo=")))
        .andExpect(status().isUnsupportedMediaType());
    perform(
            as(post("/api/v1/comics"), IMPORTER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(importBody("@@@")))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("REQ-018: 대화로 찾은 만화의 등장인물 소개를 바꿔 예전 만화에 얼굴 그림을 채운다")
  void cast() throws Exception {
    String alice = PublicId.generate();
    String conversation = PublicId.generate();
    String comicId = register(alice, conversation);

    perform(
            put("/internal/comics/by-conversation/{conversationId}/cast", conversation)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    [{"role":"할머니","name":"하늘","lookId":null,"portraitKey":"comics/%s/cast-0.png"},
                     {"role":"손녀","name":"도윤","lookId":"aaaaaaaaaaaaaaaaaaaaa","portraitKey":null}]
                    """
                        .formatted(conversation)))
        .andExpect(status().isNoContent())
        .andDo(
            doc(
                "internal-comics-cast",
                pathParameters(parameterWithName("conversationId").description("대화 ID")),
                requestFields(
                    fieldWithPath("[].role").description("등장인물 호칭"),
                    fieldWithPath("[].name").description("캐릭터 이름 (못 찾았으면 가명)"),
                    fieldWithPath("[].lookId")
                        .type(JsonFieldType.STRING)
                        .optional()
                        .description("캐릭터 모습 ID. 없으면 null"),
                    fieldWithPath("[].portraitKey")
                        .type(JsonFieldType.STRING)
                        .optional()
                        .description("만화 컷에서 잘라 낸 얼굴 그림 객체 키. 없으면 null"))));
    perform(as(get("/api/v1/comics/{id}", comicId), alice))
        .andExpect(jsonPath("$.cast[0].portrait").value(true))
        .andExpect(jsonPath("$.cast[1].portrait").value(false))
        .andExpect(jsonPath("$.concept").value("WATERCOLOR"));
    perform(as(get("/api/v1/comics/{id}/image", comicId).param("cast", "0"), alice))
        .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.containsString("/cast-0.png")));

    // 다른 폴더의 키, 너무 많은 인물은 400, 없는 대화는 404
    perform(
            put("/internal/comics/by-conversation/{conversationId}/cast", conversation)
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"role\":\"a\",\"name\":\"b\",\"portraitKey\":\"secrets/x.png\"}]"))
        .andExpect(status().isBadRequest());
    String many =
        "["
            + String.join(",", java.util.Collections.nCopies(11, "{\"role\":\"a\",\"name\":\"b\"}"))
            + "]";
    perform(
            put("/internal/comics/by-conversation/{conversationId}/cast", conversation)
                .contentType(MediaType.APPLICATION_JSON)
                .content(many))
        .andExpect(status().isBadRequest());
    perform(
            put("/internal/comics/by-conversation/{conversationId}/cast", PublicId.generate())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
        .andExpect(status().isNotFound());
  }
}
