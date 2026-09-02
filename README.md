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

Java 21 · Docker Desktop이 필요합니다. 설치는 `myDocs/07_기술스택_레포구성.md` §5-4를 따릅니다.

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
├── auth/         JWT 발급·검증 · Bearer 필터 · 데모 로그인 (카카오는 07 §10 스파이크 후)
├── user/         User · GET /users/me
├── rules/        ★ 규칙 엔진 — cluster · shrinkage · verdict · saving · aggregate · RuleParams (정민규)
├── ai/           ★ AiClient — AI POST /chat 호출의 유일한 지점 · snake_case 변환
├── internalai/   AI가 부르는 /internal/ai/* 6종 · X-Internal-Secret 필터
├── internaltest/ /internal-test/ai-ping
├── common/       ApiResponse · ErrorCode · 공유 enum
└── config/       SecurityConfig
src/main/resources/
├── application.yml          rules.* 파라미터 · 환경 변수 바인딩
└── db/migration/V1__init.sql
```

규칙과 함정은 [AGENTS.md](./AGENTS.md), 브랜치·커밋·PR은 [CONTRIBUTING.md](./CONTRIBUTING.md)를 보세요.

## 배포

`Dockerfile`이 bootJar를 `eclipse-temurin:21-jre`에 담습니다. EC2 + Docker Compose로 AI 서버와 함께 배포합니다 (07 §1 공통 인프라).
