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
├── user/         User · GET /users/me · PUT /users/me/settings (예산 · 임계값 · D+N)
├── transaction/  거래 업로드 · CSV 파서 · 조회
├── notification/ 인앱 알림 목록·읽음 · Web Push 구독 · 결제 시각 스케줄로 회고 요청 1건 생성 (FR-10)
├── retrospect/   회고 저장 · 후보 선별 · 대화 턴 프록시 · BehaviorCluster upsert (E-57~E-66)
├── analysis/     만족도 지도 · 소비 분석 · 묶음 조회 · '나만의 특징' 문장 (E-72~E-76)
├── suggestion/   재계산이 만드는 조정 제안 · 채택 · 거절 (E-81 · E-82)
├── goal/         목표 · 달성률 · 채택 제안 합산 (E-83)
├── chat/         대화 저장 · 금융 지식 Q&A 프록시 (05 #24 · FR-12)
├── rules/        ★ 규칙 엔진 — cluster · shrinkage · verdict · candidate · aggregate · saving · RuleParams (정민규)
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

## Web Push 키 설정 (FR-10)

클라이언트에서 붙이는 방법은 아래 [클라이언트 연결](#클라이언트-연결)에 있습니다.
인앱 알림은 VAPID 키 없이도 동작합니다. 키가 비어 있으면 브라우저 푸시만 꺼지고, 알림은 그대로
만들어져 `GET /notifications`에 뜹니다. 켜려면 P-256 키쌍을 만들어 세 값을 모두 채웁니다.

```bash
openssl ecparam -genkey -name prime256v1 -noout -out vapid.pem

# VAPID_PUBLIC_KEY — 클라이언트가 pushManager.subscribe에 넘길 applicationServerKey (87자)
openssl ec -in vapid.pem -pubout -outform DER | tail -c 65 | base64 | tr -d '=\n' | tr '/+' '_-'

# VAPID_PRIVATE_KEY — 서버만 갖습니다. 커밋하지 마세요 (43자)
openssl ec -in vapid.pem -outform DER | tail -c +8 | head -c 32 | base64 | tr -d '=\n' | tr '/+' '_-'
```

`VAPID_SUBJECT`는 푸시 서비스가 문제 시 연락할 곳이며 `mailto:` 또는 https URL이어야 합니다.
세 값을 `.env`에 넣습니다. **형식이 틀리면 기동이 실패합니다** — 조용히 꺼두면 원인을 찾느라
시간을 쓰기 때문입니다.

알림 생성은 `NOTIFICATION_DAILY_CRON`(기본 30분마다)에 도는 스케줄이 본류입니다. 사용자가 앱을
열지 않아도 푸시가 나가야 하기 때문이고, 목록 조회 시 생성은 그 사이를 메우는 그물입니다. 어느
쪽이든 하루 1건 가드를 지나야 하므로 겹쳐도 두 번 생기지 않습니다 (FR-03-03). 푸시는 스케줄
경로에서만, 그것도 저장 트랜잭션을 커밋한 뒤에 나갑니다 — 목록을 여는 사용자는 이미 앱을 보고
있으므로 보내지 않습니다.

**주기는 30분 격자에 맞춥니다.** 목표 시각이 30분 격자에 내린 값이라(아래 E-71) 시간마다
(`0 0 * * * *`)로 바꾸면 목표가 `:30`인 사용자는 스케줄에 영영 걸리지 않고 목록 조회 경로만 남습니다.

보내는 **시각은 사용자마다 다릅니다** (E-71) — 후보 거래의 결제 시각에서 1시간을 빼고 07:00~21:00으로
자른 뒤 30분 격자에 내린 값입니다. 새벽 1시 결제는 07:00, 22시 결제는 21:00에 갑니다. 그래서 스케줄은
30분마다 돌면서 "지금이 이 사람의 시각인가"만 봅니다. 사용자가 시각을 고르는 설정은 없습니다.

## 클라이언트 연결

`POST /chat/finance`(#24)의 본문은 05 §3의 `task_context` 규격에서 역산했고, Web Push 3종은
FR-10을 위해 신설했습니다. **05에 반영할 때 이 절이 근거입니다.** 응답은 모두
`{ success, data }` 봉투입니다 (05 §0).

### 알림 (FR-10)

| Method | Path | 설명 |
| --- | --- | --- |
| GET | `/notifications` | `{ unreadCount, notifications[] }`. 여는 순간 그날의 회고 요청 1건이 없으면 만듭니다 |
| POST | `/notifications/{id}/read` | 읽음 처리. 이것이 곧 후보 제외입니다 — 별도 상태를 저장하지 않습니다 (E-49) |
| GET | `/notifications/push-key` | `{ enabled, publicKey }`. `enabled: false`면 구독을 시도하지 말고 인앱 알림만 씁니다 |
| POST | `/notifications/push-subscriptions` | 브라우저 `PushSubscription.toJSON()`을 **그대로** 보냅니다 |
| DELETE | `/notifications/push-subscriptions?endpoint=…` | 구독 해지. 없는 구독을 지워도 성공입니다 |

### Web Push 붙이기

브라우저는 **HTTPS에서만** Push를 허용합니다 (`localhost`만 예외). iOS Safari는 사용자가
**홈 화면에 추가한 PWA**에서만 동작하고, 그 전에는 `Notification.requestPermission()`이 거부됩니다.

```js
// 1) 서버가 켜져 있는지 확인하고 공개키를 받는다
const { data } = await api.get('/notifications/push-key');
if (!data.enabled) return;                       // 인앱 알림만 쓴다

// 2) service worker 등록 + 권한 요청
const registration = await navigator.serviceWorker.register('/sw.js');
if ((await Notification.requestPermission()) !== 'granted') return;

// 3) 구독하고 서버에 그대로 넘긴다
const subscription = await registration.pushManager.subscribe({
  userVisibleOnly: true,                         // 크롬은 false를 거부한다
  applicationServerKey: urlBase64ToUint8Array(data.publicKey),
});
await api.post('/notifications/push-subscriptions', subscription.toJSON());

// base64url → Uint8Array. 문자열을 그대로 받는 브라우저도 있지만 전부는 아니다.
function urlBase64ToUint8Array(base64Url) {
  const padded = (base64Url + '='.repeat((4 - (base64Url.length % 4)) % 4))
    .replace(/-/g, '+').replace(/_/g, '/');
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
}
```

서버가 보내는 본문은 `{ title, body, url }` 세 개뿐입니다.

```js
// public/sw.js
self.addEventListener('push', (event) => {
  const { title, body, url } = event.data.json();
  event.waitUntil(self.registration.showNotification(title, { body, data: { url } }));
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(clients.openWindow(event.notification.data.url));
});
```

구독이 폐기되면(브라우저 재설치·장기 미사용) 푸시 서비스가 404·410을 돌려주고 **서버가 그 행을
스스로 지웁니다.** 클라이언트는 `pushsubscriptionchange` 이벤트에서 다시 구독해 등록하면 됩니다.

### 대화형 회고 — `POST /retrospects/chat` (05 §2 · E-63)

**상태 없는 프록시입니다.** 회고 대화는 서버에 쌓지 않으므로 화면이 `step` · 지금까지 확인한 값 ·
최근 대화를 들고 다닙니다.

```jsonc
// 요청
{
  "transactionId": 1043,
  "message": "혼자 배고파서 그냥 시켰는데 별로였어요",
  "step": "SATISFACTION",
  "reflection": { "satisfaction": "UNKNOWN", "purpose": null, "companion": null, "repeatIntent": null },
  "recentMessages": [{ "role": "assistant", "content": "만족하셨나요?" }]
}
```

`step`은 `INTRO` · `SATISFACTION` · `PURPOSE` · `COMPANION` · `REPEAT` · `CONFIRM` 중 하나입니다.
`INTRO`는 사용자 입력 없이 시작하는 턴이라 `message`를 생략합니다 — 그 턴에서 AI가
"왜 이 거래를 골랐는지"를 설명합니다 (FR-04-10·11). `recentMessages`는 서버가 최근 6개만 넘깁니다.

```jsonc
// 응답 data
{
  "reply": "혼자 드신 충동 소비로 보이는데, 맞을까요?",
  "step": "PURPOSE",
  "reflection": { "satisfaction": "LOW", "purpose": "충동", "companion": "혼자", "repeatIntent": null },
  "needsClarification": true,
  "uncertainFields": ["repeatIntent"],
  "fallback": false
}
```

- `reflection`은 **AI가 추측한 후보값이고 아직 저장되지 않았습니다.** 화면이 확인 버튼으로
  보여주고, 사용자가 고른 값만 `POST /retrospects`로 저장합니다 (E-20 · FR-04-07).
- `step`은 서버가 계산한 **다음 단계**입니다 — 응답 `reflection`에서 아직 미확정인 첫 항목이고,
  전부 확정이면 `CONFIRM`. 다음 턴에 그대로 돌려보냅니다.
- `uncertainFields`에는 AI가 되물으라고 한 항목에 더해 **서버가 표준 태그 밖이라 버린 항목**도 들어갑니다.
- `fallback: true`면 LLM 없이 템플릿으로 답한 것이니 템플릿 모드 배너를 띄웁니다 (S11 · E-38).
- 남의 거래는 **404**, 이미 회고한 거래는 **409 `DUPLICATE_RETROSPECT`**, AI가 죽으면
  **503 `LLM_UNAVAILABLE`** 입니다. 알림·거래 등 나머지 화면은 그대로 동작합니다.

### 금융 지식 Q&A — `POST /chat/finance` (05 #24 · P2)

```jsonc
// 요청 → 응답 data
{ "message": "연금저축 세액공제가 뭐예요?" }
{ "reply": "연금저축 상품에 가입하고 납입한 금액에 대해 …", "fallback": false }
```

이전 질문의 맥락은 서버가 들고 있으므로 "그럼 한도는요?" 같은 되물음이 그대로 이어집니다.
출처는 별도 필드가 아니라 문장 안에 언급됩니다 (E-47). 근거를 못 찾으면 지어내지 않고
"확인할 수 없어요"라고 답하는 것이 정상입니다 (FR-12-02).

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

### 이미지 정리

배포가 성공하면 워크플로가 **이 저장소가 올린 이미지**(`jinocc/sottaejap-server`) 중 방금 띄운 태그와
`latest`를 뺀 나머지를 지웁니다. `sottaejap-ai`도 자기 이미지만 같은 방식으로 지웁니다.
한 저장소의 배포가 다른 저장소의 이미지를 지우지 않게 하기 위해서입니다.

**그래서 두 저장소 어느 쪽도 아닌 이미지는 자동으로 지워지지 않습니다.** 예를 들어
`deploy/docker-compose.yml`의 `pgvector/pgvector:pg18`을 다음 버전으로 올리면, 옛 `pg18` 이미지는
EC2에 그대로 남습니다. 루트 볼륨이 8GB라 사람이 직접 치워야 합니다.

```bash
# EC2에서 — 무엇이 얼마나 남아 있는지 먼저 본다
docker image ls
df -h /
# 확인한 뒤 특정 이미지만 지운다
docker rmi pgvector/pgvector:<옛 태그>
```
