# AGENTS.md

이 저장소에서 코딩 에이전트(Claude Code · Codex)가 먼저 읽는 파일입니다. **규칙의 본문은
여기에 두지 않고 정본 문서를 가리킵니다.** 여기에는 **모르면 반드시 틀리는 전제**만 적습니다.

## 정본 문서

| 알고 싶은 것                     | 문서                                             |
| -------------------------------- | ------------------------------------------------ |
| 모든 결정의 출처                 | `sottaejap-docs/01_결정로그.md` (충돌 시 이 문서가 우선) |
| 엔티티 · 파생값 산식 · CSV 파싱  | `sottaejap-docs/04_데이터모델_ERD.md`                    |
| API 경로 · DTO · enum · 오류 코드 · AI 연동 규격 | `sottaejap-docs/05_API_명세서.md`                        |
| 스택 버전 · 폴더 구조 · 환경 변수 · OS 규칙 | `sottaejap-docs/07_기술스택_레포구성.md`                 |
| 브랜치 · 커밋 · PR · 검사 명령   | [CONTRIBUTING.md](./CONTRIBUTING.md)             |
| 설치와 실행                      | [README.md](./README.md)                         |

`sottaejap-docs/`는 별도 저장소 [jittaejap/sottaejap-docs](https://github.com/jittaejap/sottaejap-docs)입니다. 이 저장소와 같은 부모 폴더에 clone해 두고 경로는 그 기준으로 읽습니다.

## Spring Boot 4 — 3.x 예제를 그대로 옮기면 틀린다

Boot **4.1.1** · Spring Framework 7 · Security 7 · **Jackson 3** · Java **21**. 버전은 07 §1(v1.5)에 고정돼 있습니다.

- Jackson 3의 패키지는 `tools.jackson`입니다. `ObjectMapper`·`JsonMapper`는 `tools.jackson.databind`에서
  가져옵니다. `@JsonProperty`·`@JsonInclude` 같은 어노테이션만 `com.fasterxml.jackson.annotation`에 남아 있습니다.
- Security 7에는 `AntPathRequestMatcher`가 없습니다. `requestMatchers("/path/**")` 문자열 패턴을 씁니다.
- 테스트에서 `@MockBean`은 없습니다. `@MockitoBean`이거나, 컨텍스트 없이 `MockMvcBuilders.standaloneSetup`을 씁니다.
- 스타터 이름이 다릅니다. `spring-boot-starter-web`이 아니라 `spring-boot-starter-webmvc`입니다.
- springdoc은 **3.1.x**입니다. 3.0.x는 Boot 4.0용, 2.x는 Boot 3용이라 기동이 깨집니다 (E-35).

## 규칙 엔진 `rules/`는 결정론이다 (E-18 · NFR-01)

- HTTP · LLM · 난수 · 현재시각을 참조하지 않습니다. `RuleEngineDeterminismTest`가 소스를 스캔해 막습니다.
  실패하면 테스트를 고치지 말고 그 참조를 규칙 밖(service 계층)으로 옮깁니다.
- 숫자 상수를 코드에 쓰지 않습니다. `k`·롤업 기준·보류 임계값·축 경계는 전부 `application.yml`의 `rules.*` →
  `RuleParams`입니다. 값이 `null`이면 기본값으로 대체하지 말고 계산을 거부합니다. 9/7 튜닝은 값 주입만으로 끝나야 합니다.
- `quadrant`(좌표 4)와 `verdict`(처방 2)는 다른 층위입니다. `verdict`는 세로축 부호만으로 정하고,
  `evaluationStatus = PENDING`이면 둘 다 `null`입니다 (E-11). DB CHECK 제약이 이를 강제합니다.
- `reasonCode`는 여기서 산출합니다. AI는 그 코드를 문장으로 재구성만 합니다 (NFR-02).

## AI와 통신하는 곳은 두 군데뿐이다 (E-19)

- Spring → AI: `ai/AiClient`가 `POST /chat` 하나만 부릅니다. 작업 종류는 `task_context.task`입니다.
  경계는 snake_case이고 변환은 `ai/dto`의 `@JsonProperty`에서만 합니다. 다른 곳에서 AI를 부르지 않습니다.
- AI → Spring: `internalai/InternalAiController`의 `/internal/ai/users/{userId}/*` 6종. `X-Internal-Secret`
  헤더를 `InternalSecretFilter`가 검사합니다. **시크릿이 비어 있으면 모든 요청을 거부합니다** — "로컬이니까" 비워 두면
  ai-ping은 되지만 AI의 역호출은 전부 401입니다.
- `AiClient`는 JDK `HttpClient`를 **HTTP/1.1로 고정**합니다. 기본값(HTTP/2 업그레이드 시도)이면 uvicorn이 본문을 버려
  `/chat`이 422 "Field required: body"를 돌려주고, Spring은 그것을 503 `LLM_UNAVAILABLE`로 보여줍니다. AI 서버가
  멀쩡한데 ai-ping이 503이면 이것부터 의심합니다.
- `AI_TIMEOUT_MS`(15초)는 AI 내부의 LLM 8초 + AI → Spring 조회 왕복을 포함해야 합니다 (07 §10 리스크 4).
  AI 응답의 `fallback`은 v1.6부터 항상 옵니다 (06 R3 완료). `OPENAI_API_KEY`가 비어 있어도 AI는 템플릿 응답 + `fallback: true` + 200이므로,
  키 없는 로컬에서 ai-ping이 200에 `fallback: true`면 정상입니다 (E-38). 구버전 AI를 만날 수 있으니 `ChatResponse.isFallback()`을 유지하고 `null`을 직접 비교하지 않습니다.
- AI의 `/chat`도 `INTERNAL_SHARED_SECRET`이 비어 있거나 헤더가 다르면 **모든 요청을 401**로 거부합니다 (E-37). 양쪽 값이 같아야 ai-ping이 200입니다.
  AI 레포는 GitHub `jittaejap/sottaejap-ai`로 rename됐습니다 (E-42).

## 응답 계약 (05 §0)

- 모든 JSON은 `ApiResponse` 봉투 `{ success, data | error{code,message} }`입니다. 실패는
  `BusinessException(ErrorCode)` → `GlobalExceptionHandler`가 변환합니다. 컨트롤러에서 직접 `ResponseEntity`로 오류를 만들지 않습니다.
- 오류 코드 문자열은 05 §0과 글자 단위로 같아야 합니다. 클라이언트가 `error.code`로 분기합니다. 코드를 추가·변경하면
  **05 문서를 먼저** 고치고 같은 PR에서 반영합니다. 문서에 없는 코드·필드를 지어내지 않습니다.
- 날짜는 ISO 8601 오프셋 문자열(`+09:00`)입니다. DTO는 `OffsetDateTime`을 씁니다. Jackson 3는 기본으로 ISO 문자열을
  내보내므로 `@JsonFormat`이 필요 없지만, 숫자 배열이 나오면 서버 버그이니 클라이언트가 덮기 전에 여기서 고칩니다.
- 인증은 `Authorization: Bearer <token>` 한 가지입니다. 쿠키·세션·refresh 회전·CSRF는 쓰지 않습니다.

## 스키마는 Flyway forward-only

- `src/main/resources/db/migration`. 적용된 파일은 고치지 않고 다음 V 번호를 추가합니다. `V2__seed_demo.sql`은 정민규 몫입니다.
- `spring.jpa.hibernate.ddl-auto=validate`입니다. 엔티티와 스키마가 어긋나면 기동이 실패합니다. 엔티티를 바꾸면 마이그레이션도 같이 씁니다.
- 컨텍스트 테스트는 `RUN_DB_INTEGRATION_TESTS=true`일 때만 돕니다. 로컬 `./gradlew build`가 초록이어도 DB 없이는
  Flyway·JPA 검증이 빠진 것입니다. CI는 항상 켭니다.

## 알려진 문서 갭 — 지어내지 말고 보고한다

- `GET /users/me`의 `analysisYearMonth`(05)는 04 User 엔티티에 없고 산출 규칙도 없습니다. 지금은 `null`입니다.
- `POST /auth/login`의 요청·응답 본문은 05에 명세가 없어 v1.5(서버 스캐폴딩)로 05 §2에 추가했습니다.

## OS 혼용 (07 §5)

- 파일·폴더명은 영문 소문자와 하이픈만. `.env`는 터미널로 만듭니다. 줄바꿈은 `.gitattributes`가 LF로 강제합니다.
- 컴파일·테스트 인코딩은 `build.gradle.kts`가 UTF-8로 고정합니다. Windows에서 한글이 깨지면 코드보다 이 설정을 먼저 봅니다.
- 커밋 메시지에 `Co-Authored-By` 트레일러를 넣지 않습니다.
- `main`에 직접 push하지 않습니다(본선 30시간만 예외). 흐름은 `Issue → 작업 브랜치 → main 대상 PR → 리뷰 → Squash and merge`이고, 규칙은 [CONTRIBUTING.md](./CONTRIBUTING.md)가 정본입니다.

## 완료 보고

작업을 끝내면 **바꾼 것 · 지킨 계약 · 실행한 검사와 결과 · 실행하지 못한 검증과 이유 · 남은 위험**을 구분해 적습니다.
"성공"으로 뭉뚱그리지 않습니다. CI 통과와 로컬 통과를 구분하고, DB 없이 돌린 빌드는 그렇게 적습니다.
