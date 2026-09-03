# sottaejap-server 협업 가이드

이 문서를 읽으면 브랜치를 만들고, 커밋하고, PR을 올리고, 병합 전 검사를 통과시킬 수
있습니다. 값의 정본은 `myDocs/07_기술스택_레포구성.md` §5·§6·§9입니다. 충돌하면 그쪽이
우선합니다.

## 1. Issue

> [!IMPORTANT]
> 모든 작업은 Issue를 만든 뒤 시작합니다.

1. 작업 목적과 완료 조건을 Issue에 작성합니다.
2. `New issue`에서 작업 성격에 맞는 양식(기능 개발 · 버그 수정 · 리팩터링 · 일반 작업)을 고릅니다.
   제목은 `[Type] 한글 설명` 형식이고, 양식이 접두사 `[Feat]` `[Fix]` `[Refactor]` `[Docs]` `[Chore]` `[Test]`를 붙입니다.
3. 한 Issue에는 하나의 주요 목적만 둡니다.
4. 최신 `main`에서 Issue 번호가 포함된 작업 브랜치를 만듭니다.
5. API, 환경 변수, 배포 또는 공통 계약을 변경하면 관련 문서도 같은 PR에서 수정합니다 (§6).

기본 브랜치는 `main`입니다. `develop` 브랜치는 사용하지 않습니다.

작업 흐름은 `Issue → 작업 브랜치 → main 대상 PR → 리뷰 → Squash and merge`입니다.

> **본선 30시간 예외 (07 §9 · E-27):** 리뷰 대기로 막히지 않도록 Issue 없이 작업하고 `main`에
> 직접 push할 수 있습니다. 그 밖의 기간에는 위 흐름을 따릅니다.

## 2. 브랜치

브랜치 이름은 `<type>/#<issue-number>-<short-description>` 형식을 사용합니다. 설명은
영문 소문자 kebab-case로 짧게 씁니다.

| Type       | 용도                                |
| ---------- | ----------------------------------- |
| `feature`  | 기능 추가                           |
| `fix`      | 버그 수정                           |
| `refactor` | 동작을 유지하는 구조 개선           |
| `chore`    | 설정, 의존성, 빌드 또는 인프라 작업 |
| `test`     | 테스트 추가 또는 수정               |
| `docs`     | 문서만 변경                         |

```text
feature/#12-csv-parser
fix/#27-jwt-expiry
docs/#31-jackson-note
```

`main`에 직접 push하지 않습니다. 모든 변경은 PR을 통해 병합합니다. 본선 30시간 예외는 §1과
같습니다.

## 3. 커밋

커밋 메시지는 `<type>: <한글 요약>` 형식을 사용합니다. 변경 영역을 구분해야 한다면
`<type>(<scope>): <한글 요약>`을 사용하세요. 범위는 폴더나 화면 이름입니다. 관련 Issue 번호는 메시지 끝에
`(#12)` 형태로 추가합니다.

```text
feat(parser): 국민카드 CSV 헤더 매핑 (#12)
fix(auth): 만료 토큰에 401 UNAUTHORIZED 반환 (#27)
chore(deps): springdoc 3.1.0으로 고정
docs(agents): Jackson 3 패키지 주의 추가 (#31)
```

| 타입       | 용도                      |
| ---------- | ------------------------- |
| `feat`     | 기능 추가                 |
| `fix`      | 버그 수정                 |
| `refactor` | 동작을 유지하는 구조 개선 |
| `chore`    | 설정, 의존성, 빌드        |
| `test`     | 테스트 추가 또는 수정     |
| `docs`     | 문서만 변경               |

- 하나의 커밋에는 하나의 논리적인 변경만 담습니다.
- 독립적으로 설명하거나 되돌릴 수 있는 API 연동, UI, 테스트와 문서 변경은 커밋을 나눕니다.
- 의미 없는 중간 메시지와 포맷 변경만 섞인 커밋을 남기지 않습니다.
- 민감정보, 빌드 산출물과 개인 IDE 설정을 커밋하지 않습니다.
- 커밋 메시지에 `Co-Authored-By` 트레일러를 넣지 않습니다. 에이전트가 생성한 커밋도 같습니다.

## 4. PR

PR 제목은 Issue와 같은 `[Type] 한글 설명` 형식을 사용합니다.
[PR 양식](./.github/PULL_REQUEST_TEMPLATE.md)의 모든 항목을 확인하세요.

- 본문에 **무엇을 바꿨는가 · 왜 · 어떻게 검증했는가**를 적습니다. 화면을 바꿨으면 스크린샷을 붙입니다.
- `main` 대상 PR에는 `Closes #<issue-number>`를 적어 Issue를 함께 닫습니다.
- 관련 없는 변경을 한 PR에 섞지 않습니다.
- 통합 담당(고현석)이 리뷰한 뒤 병합합니다.

리뷰하고 병합할 때는 다음을 지킵니다.

- PR을 열기 전에 변경 파일과 비밀정보 포함 여부를 확인합니다.
- 리뷰 피드백을 반영하거나 반영하지 않는 이유를 답변으로 남깁니다.
- §5의 검사가 실패한 상태로 병합하지 않습니다.
- 코드 리뷰와 승인을 받기 전에 작성자가 직접 병합하지 않습니다.
- `Squash and merge`로 PR의 커밋을 하나의 이력으로 정리합니다. squash commit 제목은 PR 제목을
  그대로 씁니다.
- 병합 후 원격 작업 브랜치를 삭제합니다.

## 5. 병합 전 검사

CI(`.github/workflows/ci.yml`)가 PostgreSQL 서비스를 띄우고 아래를 돌립니다. 올리기 전에 로컬에서 먼저 통과시킵니다.

```bash
./gradlew build --no-daemon
```

DB가 필요한 컨텍스트 테스트는 `RUN_DB_INTEGRATION_TESTS=true`일 때만 돕니다. API·인증·DB를 바꿨다면 로컬에서
`docker compose up -d db` 후 켜서 돌리고, 아래 중 해당하는 것을 확인해 PR에 적습니다.

- Swagger UI(`/swagger-ui.html`)가 뜨는지
- 응답 본문과 오류 코드
- Flyway 적용 결과와 `ddl-auto=validate` 통과
- AI 연동을 바꿨다면 `/internal-test/ai-ping` 200

## 6. 계약 변경 절차

API 경로, DTO, 공유 enum을 바꾸면 세 레포가 같이 깨집니다 (07 §6).

1. `myDocs/05_API_명세서.md`를 **먼저** 고칩니다. 문서가 계약입니다.
2. 팀 채널에 `[계약변경] verdict enum 2종으로` 형태로 한 줄 공지합니다.
3. 서버에서는 `common/enums`와 `CommonErrorCode`·DTO를 문서에 맞춰 고칩니다. AI 연동 규격(05 §3)을 바꿨다면
   통합 담당(고현석)이 `sottaejap-ai`의 `SpringClient`·`ChatRequest` 반영을 확인합니다.

코드가 문서를 앞서지 않습니다.

## 7. 버전과 도구

`build.gradle.kts`의 버전은 07 §1 표(v1.5)를 그대로 고정한 값입니다. 임의로 올리지 않습니다.
Boot BOM이 관리하는 라이브러리(Flyway·Hibernate·Jackson 등)에 버전을 따로 적지 않습니다.
springdoc은 `3.1.x`를 유지합니다. 3.0.x는 Boot 4.0용이라 기동이 깨집니다.

Gradle 버전은 `gradle/wrapper/gradle-wrapper.properties`가 정본입니다. 바꿀 때는
`./gradlew wrapper --gradle-version <버전>`으로 바꾸고 wrapper 파일을 같은 커밋에 넣습니다.

## 8. macOS · Windows 혼용

07 §5가 정본입니다. 여기서는 클라이언트에서 실제로 걸리는 것만 적습니다.

- 최초 1회: macOS는 `git config --global core.autocrlf input`, Windows는 `false`.
- `.env`는 터미널로 만듭니다. `cp .env.example .env` / `Copy-Item .env.example .env`.
  Windows 탐색기는 `.env.txt`를 만듭니다.
- Gradle은 macOS `./gradlew`, Windows `.\gradlew.bat`입니다.
- 파일·폴더명은 영문 소문자와 하이픈만 씁니다.
- 줄바꿈은 `.gitattributes`가 LF로 강제합니다. diff가 파일 전체로 뜨면 CRLF가 섞인 것입니다.
- CSV 테스트 픽스처는 UTF-8과 EUC-KR 두 인코딩을 모두 둡니다 (07 §5-1).

## 9. 비밀값

`JWT_SECRET`·`AI_SHARED_SECRET`·`KAKAO_CLIENT_SECRET`·DB 비밀번호를 코드·로그·PR에 남기지 않습니다.
`.env`는 커밋하지 않고 `.env.example`만 커밋합니다. CI의 값은 러너 안에서만 유효한 더미입니다.
