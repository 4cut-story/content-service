# content-service

**완성된 4컷을 모으고 나누는 곳이에요. 내 스토리, 게시, 피드, 좋아요, 댓글, 팔로우.**

[← 4cut story 소개](https://github.com/4cut-story) · [데모](https://4cut.java21.net) · [아키텍처](https://github.com/4cut-story/.github/blob/main/docs/architecture.md)

## 하는 일

- 내 스토리: conversation-service가 완성한 만화를 받아 보관해요 (`/internal/comics`). 등장인물, 줄거리 요약, 라이브 모드 정보도 함께 받아요.
- 게시: 캡션과 공개 범위를 정해 올리고, 조회수를 세요 (`/api/v1/posts`)
- 피드: 공개된 게시물과 팔로우한 사람의 게시물 (`/api/v1/feed`)
- 좋아요, 댓글, 팔로우와 팔로워 수
- 이미지는 MinIO에 두고, 미리 서명한 주소로만 내보내요.
- 탈퇴한 사용자의 데이터 지우기 (`/internal/users/{userId}/data`)

## 시스템 안에서의 위치

```
web ──> api-gateway ──> content-service ──> PostgreSQL, MinIO
                             ^
conversation-service ────────┘  완성한 만화 넘기기
```

## 기술

Java 21 · Spring Boot 4 · Spring Data JPA · Flyway · PostgreSQL · AWS SDK v2 (MinIO)

## 실행

```bash
DB_URL=jdbc:postgresql://localhost:5432/fourcut DB_USERNAME=... DB_PASSWORD=... \
MINIO_ENDPOINT=http://localhost:9000 MINIO_ACCESS_KEY=... MINIO_SECRET_KEY=... MINIO_BUCKET=... \
./mvnw spring-boot:run
```

로컬 포트는 8083이에요.

## 검사

```bash
./mvnw verify          # 테스트, 정적 분석, 커버리지 80%
./mvnw spotless:apply  # 코드 스타일 고치기
```

## 자세한 문서

| 문서 | 내용 |
| --- | --- |
| [아키텍처](https://github.com/4cut-story/.github/blob/main/docs/architecture.md) | 서비스별 역할, 저장소, 통신 방식 |
| [만화 피드 스펙](https://github.com/4cut-story/docs/tree/main/specs/002-comic-feed) | 게시, 피드, 좋아요, 댓글 요구사항 |
| [common](https://github.com/4cut-story/common) | OpenAPI 계약, 빌드와 CI 기준 |
