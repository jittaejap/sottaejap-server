# sottaejap-server

**규칙 엔진(결정론적)을 소유하는** 소때잡의 Spring Boot 서버입니다. 같은 입력에는 항상 같은 묶음·보정·판정을 냅니다 (NFR-01).
AI 서버(`sottaejap-ai`)는 입구(회고 후보 제안)와 출구(설명)에만 있고, 판단과 집계는 전부 이 저장소의 `rules/`가 합니다 (결정로그 E-18).

이 문서를 읽으면 로컬에서 DB와 서버를 띄우고, AI 서버와의 왕복을 확인하고, 검사 명령을 돌릴 수 있습니다.

## 역할

| 이 저장소가 하는 것 | 하지 않는 것 |
| --- | --- |
| 인증(카카오 OAuth · 데모 계정) · 저장/조회 API 전부 · CSV 파싱 | 자연어 이해 · Tool Calling · 설명 문장 생성 (AI 서버) |
| **규칙 엔진 전부** — 후보 선별 · 묶음 · 롤업 · 축소 추정 · 판정 · 부담 · 절감 · 집계 | 임베딩 · RAG 저장 (AI 서버, P2) |
| AI 서버에 열어주는 `/internal/ai/*` 6종 · AI `/chat` 호출(`AiClient`) | |
| **Flyway 마이그레이션 단독 소유** | |

## 시작하기

Java 21 · Docker Desktop이 필요합니다. 설치는 `sottaejap-docs/07_기술스택_레포구성.md` §5-4를 따릅니다.

```bash
cp .env.example .env            # Windows: Copy-Item .env.example .env
# .env의 JWT_SECRET을 채운다: openssl rand -base64 48
docker compose up -d db          # pgvector/pgvector:pg18, :5432
./gradlew bootRun                # Windows: .\gradlew.bat bootRun — Flyway가 V1을 적용하고 :8080에서 뜬다
```

확인:

```bash
curl -s localhost:8080/actuator/health
curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' -d '{"provider":"LOCAL"}'
curl -s localhost:8080/users/me -H "Authorization: Bearer <accessToken>"
curl -s localhost:8080/internal-test/ai-ping     # AI 서버(:8000)가 떠 있어야 200 — 9/2 성공 기준 (07 §4)
```

Swagger UI는 `http://localhost:8080/swagger-ui.html`입니다.

## 검사 명령

CI(`.github/workflows/ci.yml`)가 같은 명령을 PostgreSQL 서비스와 함께 돌립니다.

```bash
./gradlew build --no-daemon
```

DB가 필요한 컨텍스트 테스트는 로컬에서 기본으로 건너뜁니다. DB를 띄운 뒤 `RUN_DB_INTEGRATION_TESTS=true ./gradlew test --no-daemon`으로 돌립니다.

## 구조

```
src/main/java/kr/sottaejap/server/
├── auth/         JWT 발급·검증 · Bearer 필터 · 데모 로그인 · kakao/ 인가 코드 교환·프로필 조회 (E-55)
├── user/         User · GET /users/me
├── transaction/  거래 업로드 · CSV 파서 · 조회
├── rules/        ★ 규칙 엔진 — cluster · shrinkage · verdict · saving · aggregate · RuleParams (정민규)
├── ai/           ★ AiClient — AI POST /chat 호출의 유일한 지점 · snake_case 변환
├── internalai/   기계가 부르는 경로 — /internal/ai/* 6종 · X-Internal-Secret 필터 · /internal-test/ai-ping
├── common/       ApiResponse · ErrorCode · 공유 enum
└── config/       SecurityConfig
src/main/resources/
├── application.yml          rules.* 파라미터 · 환경 변수 바인딩
└── db/migration/V1__init.sql
```

도메인 패키지(`user` · `transaction` · 앞으로의 `goal` · `retrospect` …)는 `controller` · `service` ·
`repository` · `dto` · `domain` 다섯 하위 패키지를 씁니다. 엔티티가 없는 기술 패키지(`auth` · `ai` ·
`internalai` · `common` · `config` · `rules`)는 역할 이름을 그대로 씁니다 — 빈 `repository/`를 만들지 않습니다.

규칙과 함정은 [AGENTS.md](./AGENTS.md), 브랜치·커밋·PR은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 보세요.

## 배포

`main`에 병합되고 **CI가 통과하면** `.github/workflows/deploy.yml`이 자동으로 배포합니다.
GitHub Actions가 이미지를 굽고, EC2는 받아서 켜기만 합니다 (프리티어 메모리로는 Gradle 빌드가 죽습니다).

```text
CI 통과 → 이미지 빌드 → Docker Hub push → EC2 SSH → compose 전송 → pull·up -d → 헬스체크
```

EC2 구성은 `deploy/docker-compose.yml`이 정본입니다. `sottaejap-ai`도 EC2에 있는 이 파일을 읽어 쓰므로,
**이 저장소가 최소 한 번 먼저 배포돼야** AI 배포가 동작합니다.
8080·8000은 `127.0.0.1`에만 열려 있고 바깥은 Nginx(`api.clearpng.cloud`)만 통과합니다.

### Actions Secrets

| 이름 | 내용 |
| --- | --- |
| `EC2_HOST` | 탄력적 IP 또는 `api.clearpng.cloud` |
| `EC2_SSH_KEY` | `sottaejap-key.pem` 파일 내용 전체 |
| `DOCKERHUB_TOKEN` | Docker Hub 액세스 토큰 (계정 `jinocc`). 러너의 push에만 씁니다 — 이미지가 public이라 EC2는 로그인하지 않습니다 |

### EC2 `~/apps/.env`

이 저장소가 관리하지 않습니다. 사람이 EC2에 직접 두고, 없으면 배포가 이유를 출력하고 멈춥니다.
로컬 `.env`를 그대로 복사하면 안 됩니다 — 컨테이너끼리는 `localhost`가 아니라 서비스 이름(`db` · `ai` · `server`)으로 부릅니다.

| 키 | 없으면 |
| --- | --- |
| `DB_PASSWORD` | 배포 중단. `@ : / # ?` 를 넣지 않습니다 — AI의 `DATABASE_URL`이 깨집니다 |
| `JWT_SECRET` | 배포 중단 (없으면 서버 기동 자체가 실패합니다) |
| `AUTH_ALLOWED_ORIGINS` | 배포 중단 (없으면 배포된 클라이언트가 CORS에 막힙니다) |
| `AI_SHARED_SECRET` | 배포 중단 (없으면 `/internal/ai/*`가 전부 401). AI의 `INTERNAL_SHARED_SECRET`으로도 같이 들어갑니다 |
| `OPENAI_API_KEY` | 배포 중단 |
| `KAKAO_CLIENT_ID` · `KAKAO_CLIENT_SECRET` · `KAKAO_REDIRECT_URIS` | 카카오 로그인만 불가 (비워도 기동) |
| `DEMO_ACCOUNT_ENABLED` | 기본 `true`. 데모 로그인을 닫으려면 `false` |
| `RULES_*` | 넣지 않습니다. `application.yml`의 잠정값이 그대로 쓰입니다. 값을 비운 키(`RULES_SHRINKAGE_K=`)는 잠정값이 아니라 `null`이라 회고 저장이 500이 됩니다 — 덮어쓸 때만 값과 함께 추가합니다 |

### 되돌리기

이미지 태그가 커밋 해시로 고정돼 있습니다. Docker Hub에 이전 이미지가 남아 있어 재빌드가 필요 없습니다.

```bash
# EC2에서
cd ~/apps/sottaejap-server/deploy
SERVER_TAG=<이전 커밋 해시> docker compose --env-file ~/apps/.env up -d server
```
