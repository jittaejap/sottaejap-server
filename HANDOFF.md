# sottaejap-server 인수인계 (2026-09-09 저녁)

이 문서를 읽으면 server 담당이 client 작업으로 옮겨 간 뒤 **남은 server 작업을 어디서부터 이어받을지**,
**한 건을 정본 → 이슈 → PR → 리뷰 회신 → 06 반영까지 어떻게 끝내는지**, **검증을 어떻게 돌리고 무엇에
걸리는지**를 안다. 규칙 본문은 반복하지 않는다 — [AGENTS.md](./AGENTS.md) · [CONTRIBUTING.md](./CONTRIBUTING.md) ·
[README.md](./README.md) · `sottaejap-docs/AGENTS.md`가 정본이고 여기에는 **그 문서에 없는 실전 지식**만 적는다.

기준 시각: 2026-09-10 01:15 KST(PR #63 · #72 병합 · Deploy success 반영). 상태 표는 그 순간의 스냅샷이다 — **최신은 항상 06 액션시트와 GitHub**다.

## 1. 읽는 순서

1. [AGENTS.md](./AGENTS.md) — 모르면 틀리는 전제 5개 (Spring Boot 4 · 규칙 엔진 결정론 · AI 호출 두 곳 · 회고 저장 트랜잭션 · 월간 리포트 확정).
2. [CONTRIBUTING.md](./CONTRIBUTING.md) — 브랜치 · 커밋 · PR · 검사 명령.
3. `sottaejap-docs/06_액션시트.md` 머리의 최신 메모 5~6개와 `R-번호` 표의 ☐ 행.
4. 이 문서 §2 → §3.

## 2. 지금 상태

| 항목 | 값 |
| --- | --- |
| `main` | `81aaad3` — PR #72(#71 목표 달성 예정일 — `V10` `goals.target_date` · `targetDate`) 병합 · CI · **Deploy success**(9/10 00:25 KST). 그 앞이 PR #63(#58 XLSX 선계수 · 운영 힙 `-Xmx512m` 명시 — `ef4c5d9` · 00:18 KST · Deploy success) |
| 배포 | `main` 병합 → CI 통과 → `deploy.yml`이 EC2로 자동 배포(compose 파일 scp → `pull` → `up -d server`). EC2는 **메모리 1.9 GiB · swap 2 GiB · 루트 24GB 한 대**에 `db` · `server` · `ai`가 같이 뜬다(07 §12 실측). 사이징은 **07 §12**가 정본이다 — `deploy.yml` 주석과 README 배포 문단에 있던 "t3.micro" · "루트 8GB" · "프리티어 메모리"는 실측과 달라 같은 라운드에 고쳤다 |
| 테스트 | 전체 빌드 **583건 · 실패 0 · 건너뜀 0**(`main` `81aaad3`, §5 레시피로 재측정), DB 통합 포함. 581(`ef4c5d9`) → 583(`81aaad3`)으로 #72가 더한 만큼 늘었다. CI는 `RUN_DB_INTEGRATION_TESTS=true`로 돈다 |
| 열린 PR | **#77**(#76 — 이 문서의 상태 표와 `deploy.yml` · README의 EC2 사양 주석 정정) · **#78**(#74 사용자 기준 금액 — `V11` · `outlierBaseAmount`) 둘이다. #63 · #72는 병합됐다 |
| 열린 이슈 | **#73**(3-레포 연동 리허설 — §3-3을 하나로 묶었다) · **#74**(사용자 기준 금액 — client #33 후속 → PR #78) · **#75**(`/internal-test/ai-ping`이 운영 도메인에서 무인증 200 — #73 항목 2 실측에서 나왔다) · **#76**(→ 이 PR) 넷이다. **#58은 PR #63, #71은 PR #72 병합으로 닫혔다**(`Closes` 줄). 9/9 저녁에도 한 번 정돈했다 — #60 · #66 · #57을 #67로 통합해 PR #68로 닫았고, #49는 닫아 06 R33으로만 추적한다(06 v2.52 · v2.56) |
| docs | 01 v2.41 · 04 v2.17 · 05 v2.38 · **06 v2.65**(`9d542d2`) · **07 v2.17**. 06이 정본 진행표다. #74는 **R36**(06 v2.64 · 01 E-115)에 있고, **#73 · #75는 아직 R 행이 없다** |

오늘(9/9) 병합된 것: #42(#36) · #44(#20) · #45(#19) · #46(#43) · #48(#47) · #53(#51) · #54(#52) · #55(#50) · #59(#56) · #65(#64) · #62(#61) · #68(#67) · #70(#69) · **#63(#58 — 9/10 00:18 KST)** · **#72(#71 — 9/10 00:25 KST)**.
전부 06에 병합 해시 · Deploy 결과까지 적혀 있다.

## 3. 남은 작업과 다음 한 걸음

### 3-1. 결정 없이 지금 할 수 있는 것

| 이슈 | 다음 한 걸음 | 크기 |
| --- | --- | --- |
| **#73** 3-레포 연동 리허설 | EC2 배포본으로 client → server → ai를 한 번 완주하는 묶음 이슈다. §3-3의 다섯 줄 · 07 §10 리스크 4(타임아웃 예산) · 06 R32 잔여 · 운영 `.env`를 한 번에 본다. **사람이 배포본에 접근해야 시작된다.** 항목 2(`/internal*` 외부 노출)는 실측이 끝나 **#75**로 빠졌다 | M |
| **#74** 사용자 기준 금액(원) → **PR #78 리뷰·병합 대기** | 정본(01 v2.41 **E-115** · 04 v2.17 · 05 v2.38 · 06 R36)이 먼저 올라갔고 코드(`V11` `users.outlier_base_amount` · `PUT /users/me/settings` · `GET /users/me` · `THRESHOLD_EXCEEDED`가 사용자 금액을 먼저 본다)가 PR #78에 있다. 남은 것은 리뷰 · 병합과 병합 뒤 06 R36 상태 칸 갱신뿐이다. **프리셋 → 금액 매핑과 화면은 client #33 몫**이다 | S |
| **#75** `/internal-test/ai-ping` 무인증 노출 | #73 항목 2 실측(9/10 00:20 KST)에서 나왔다 — 운영 도메인에서 헤더 없이 200이고 호출마다 AI `/chat`을 한 번 부른다. 이슈가 권하는 방향은 **코드에서 막는 것**이다: `InternalSecretFilter`(`internalai/InternalSecretFilter.java` 25행)의 `PATH_PREFIX`를 넓히고, README "시작하기" · 07 §4 · `AGENTS.md`의 확인 명령에 `X-Internal-Secret`을 붙인다. **이슈 본문이 적은 `/internal/`로는 막히지 않는다** — 경로가 `/internal-test/ai-ping`이라 `startsWith("/internal/")`가 거짓이다. 슬래시 없는 `/internal`이거나 접두사 둘을 다 보는 것이어야 한다. Nginx `location ^~ /internal { return 404; }`는 사람이 EC2에서 하는 일이라 README 배포 절에 절차만 적는다 | S |

### 3-2. 입력이 있어야 시작되는 것

| 항목 | 막힌 곳 | 입력이 오면 |
| --- | --- | --- |
| **R29** 카테고리 매핑 (P0 · FR-02-03) | **3사 원본 카테고리 값 목록이 어디에도 없다.** server · docs에 CSV 표본 0건 | 04 §4 "내부 통합 카테고리(초안)" 10종에 대응표 초안을 04에 먼저 올리고 확인 → `TransactionServiceImpl.category()` 변환 + **백필 마이그레이션 1회**(다음 V 번호는 **`V12`** — `V10`은 #71(PR #72 병합), `V11`은 #74(PR #78)가 쓴다)(`category` ← `source_category` 매핑) + **전체 묶음 재계산 1회**(E-58 — `clusterKey`의 카테고리 · 시간대 자리가 같이 움직인다) + 05 §2 `category`를 enum으로 되돌림(05 v2.21 취소). 06 R29 · 04 v2.15 |
| **R33** highlight 캐시와 가드레일 폴백 (구 #49 — 닫았다) | 리허설에서 `fallback: true` 비율 | A(폴백도 캐시) / B(원인 필드 계약 변경, 05 먼저) / C(그대로) 중 하나를 01에 E-번호로. 셋의 장단은 **06 R33 행**에 그대로 있다 — 이슈는 코드로 밟을 다음 걸음이 없어 닫았고 R33으로만 추적한다(06 v2.52). 코드가 필요해지면 그때 새 이슈 |

### 3-3. EC2 리허설에서 볼 것 (사람 접근 필요) — **이슈 #73으로 묶었다**

배포본으로 한 번씩 확인하고 06에 결과를 적는다. 하나라도 어긋나면 이슈로.
아래 다섯 줄은 #73 `작업 범위`에 통과 기준까지 옮겨 두었다 — 진행은 그쪽에서 한다.

- 회고 저장 직후 `GET /suggestions`의 `reason`이 템플릿 → 다음 조회에서 AI 문장으로 바뀌는가 (`@Async` 배선, 06 v2.36 메모 — 단위 테스트 밖).
- `GET /analysis` 두 번째 진입이 `ANALYSIS_NARRATE`를 다시 부르지 않는가 (E-102 캐시, `OPENAI_API_KEY` 있어야 체감).
- `fallback: true` 비율 — 06 R33 입력(구 #49).
- 20,001행 CSV → 400 `TOO_MANY_ROWS` · 3MB xlsx → 400(OOM이면 500, #58 — PR #63이 배포본에 들어갔다).
- **PR #63의 운영 사이징이 실제로 붙었는가** — `docker inspect ... --format '{{.HostConfig.Memory}}'`가 `1073741824`, 컨테이너 로그에 `Picked up JAVA_TOOL_OPTIONS: -Xmx512m`. 이어서 `docker stats --no-stream`의 `server` RSS와 `free -h`의 swap을 적는다. **07 §12-4 표는 명시 전 값**이라 실측이 끝나면 그 자리에 배포 후 값을 덧붙이고, 실사용이 상한에 붙어 있으면 `mem_limit`을 같은 라운드에 고친다.
- `POST /chat/analysis` 왕복(1.0~1.5초, 06 v2.46) · `recentMessages`에 `role: system` → 400.

### 3-4. 짝 이슈 — server가 지켜볼 것

다른 저장소 몫이지만 server 계약과 물려 있다. 그쪽이 닫히면 05 · 06의 해당 줄을 갱신한다.

| 저장소 | 이슈 | server와 물린 곳 |
| --- | --- | --- |
| client | **PR #28 후속(미채번)** | 온보딩이 고른 **목표 달성 예정일**을 `createGoal` · `updateGoal`이 `targetDate`(`YYYY-MM-DD`)로 보내고 `GET /goals`에서 읽는 일 + 마이페이지 "기간(개월)" 정리. server #71이 계약을 먼저 세웠다 — **PR #72(`81aaad3`) 병합 · Deploy success로 R35의 server 몫은 닫혔다**(06 v2.64). 반대로 했으면 서버가 모르는 필드를 400도 없이 버렸다(06 R35 · E-114) |
| client | **#33** | 온보딩 2단계 · 마이페이지의 **기준 금액(원)** 입력. 지금은 Pinia 메모리에만 남는다 — server가 `outlierBaseAmount` 칸을 먼저 만든다(server #74 → **PR #78** 리뷰 대기). 계약 없이 보내면 #71과 같은 조용한 버림이다 |
| client | #22 · #15 | `/chat/analysis` 배선 · 거래 목록 배선(R30). 닫힌 것: #24(`TOO_MANY_ROWS` `message` 표시 · 05 v2.35) · #16 · #30(표준 태그 라벨/값 분리 · R31 ✅ · 06 v2.53 · v2.55) |
| ai | #55 닫힘 | assistant `reply` 2,000자 보장 — server E-110의 짝. 닫혔으니 05 §3의 해당 줄만 확인 |
| ai | #48 | `ANALYSIS` 숫자 가드 — server #49 결정의 입력 |
| ai | **#62 닫힘** · #69 | **EC2 운영 DB `financial_chunks`에 금융 문서 8개(741행 · 8 source)가 들어갔다**(06 v2.65). 테이블 소유자는 server(`V8`)지만 이번엔 **행만 늘었고 스키마·계약은 그대로**다 — server가 할 일은 없다. 배포본에서 `FINANCE_QA`가 "확인할 수 없다" 대신 근거 있는 답을 낸다(예금자보호 한도 → 1억원). 새로 연 **#69**는 그 근거 경로에서 답변에 반말이 섞이는 문체 문제다(권유 가드레일은 11회 실측 0건). **#73 리허설 때 금융 질문 한 건을 같이 던지면 된다** |

## 4. 한 건을 끝내는 절차

CONTRIBUTING의 흐름에 **실제로 해 온 순서와 산출물**을 붙인 것이다.

1. **정본 먼저.** 값 · enum · 경로 · 오류 코드가 바뀌면 `sottaejap-docs`의 01(E-번호) → 05 → 06을 먼저 고쳐 `main`에 push한다(본선 기간 직접 push 예외). §6의 함정을 본다.
2. **이슈.** `.github/ISSUE_TEMPLATE`의 항목 이름을 그대로 제목으로 쓴 마크다운을 `gh issue create --body-file`로 올린다. 라벨은 `✨ Feature` · `🐞 BugFix` · `🔨 Refactor` · `📃 Docs` · `⚙ Setting`(이름에 이모지가 있다 — `gh label list`로 확인).
3. **브랜치.** `<type>/#<이슈>-<kebab>`. 다른 사람이 같은 체크아웃을 쓰고 있을 수 있으니 **`git status`를 먼저 보고, 남의 미커밋 변경이 있으면 worktree로 분리한다** — `git worktree add ../wt-<이슈> -b <branch> origin/main`. `.env`는 `cp`로 옮긴다(출력 금지). 끝나면 `git worktree remove --force`.
4. **코드.** 바뀐 줄이 전부 이슈로 추적되게. 인접 코드 정리 · 리팩터링을 섞지 않는다.
5. **검증.** §5 레시피로 전체 빌드 + **건너뜀 0 확인**. "DB 없이 돌렸다"면 그렇게 적는다.
6. **커밋.** `<type>(<scope>): <한글 요약> (#이슈)` + 본문에 왜. `Co-Authored-By` 트레일러 없음.
7. **PR.** `.github/PULL_REQUEST_TEMPLATE.md` 전 항목. 검증 표에 **명령 · 건수 · 실패 0 · 건너뜀 0**을 적고, 하지 않은 수동 검증은 "하지 않았다"와 이유를 적는다(리뷰어가 대신 해 준 사례가 두 번 있다). 정본 커밋 해시를 PR 코멘트로 단다.
8. **리뷰 회신.** 지적 1건당 커밋 1개. 반영하지 않는 것은 이유를 쓴다. 리뷰어의 수치 · 범위 주장은 **코드에 대고 다시 확인한 뒤** 답한다 — 맞으면 "맞습니다"와 근거, 다르면 어디가 다른지(예: PR #54 B — 효과는 같고 메커니즘만 달랐다). 후속은 그 자리에서 이슈로 빼고 번호를 회신에 적는다. 회신은 존댓말.
9. **병합 뒤.** 06 해당 R 행 상태 칸에 `PR #n \`해시\` 병합 · Deploy success`, 머리에 vX.Y 메모. Deploy 결과는 `gh run list --workflow Deploy --limit 1`로 **실제로 보고** 적는다.

## 5. 검증 레시피 (복붙)

로컬 5432에는 본선 compose의 db가 떠 있고, 옛 볼륨이면 Flyway 체크섬 불일치(E-98)가 난다. **검증은 55432 일회용 컨테이너**로 한다 — 다른 사람의 5432를 건드리지 않고 체크섬 문제도 없다.

```bash
docker run -d --name sottaejap-verify \
  -e POSTGRES_DB=sottaejap -e POSTGRES_USER=sottaejap -e POSTGRES_PASSWORD=verify-only \
  -e TZ=Asia/Seoul -e PGTZ=Asia/Seoul -p 55432:5432 pgvector/pgvector:pg18
```

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:55432/sottaejap DB_USER=sottaejap DB_PASSWORD=verify-only
RUN_DB_INTEGRATION_TESTS=true ./gradlew build --no-daemon
```

OS 환경 변수가 `.env`(`spring.config.import`)보다 우선하므로 `.env`를 고치지 않아도 55432로 붙는다.
`FlywayUpgradeFromV1Test`는 `.env`가 아니라 **OS 환경 변수 `DB_URL`·`DB_USER`·`DB_PASSWORD`만** 읽는다 — 위 `export`가 그 한 건도 살린다.

**건너뜀을 센다.** `RUN_DB_INTEGRATION_TESTS`를 안 켜면 통합 35건이 조용히 건너뛰고 BUILD SUCCESSFUL이 뜬다. 초록불을 믿지 말고 합산한다.

```bash
python3 - <<'EOF'
import glob, xml.etree.ElementTree as ET
t=f=e=s=0
for x in glob.glob('build/test-results/test/TEST-*.xml'):
    r=ET.parse(x).getroot(); t+=int(r.get('tests')); f+=int(r.get('failures')); e+=int(r.get('errors')); s+=int(r.get('skipped'))
print(f"tests={t} failures={f} errors={e} skipped={s}")
EOF
```

`skipped=0`까지 PR 검증 표에 적는다. 끝나면 `docker rm -f sottaejap-verify`.

HTTP 본문까지 보려면 README "시작하기"의 `docker compose up -d --build`(db · server · ai)로 전체 스택을 띄운다. `ai`는 형제 폴더 `../sottaejap-ai`를 굽는다.

## 6. docs 갱신 — 규칙은 `sottaejap-docs/AGENTS.md`, 여기엔 걸렸던 것

- **버전 번호는 문서마다 독립 카운터다.** AGENTS의 "01 §0 번호를 따른다"를 그대로 적용하면 틀린다(9/9에 07을 v2.18로 잘못 올렸다가 v2.14로 정정). 각 문서 머리의 현재 값 +1.
- **01 §0은 블록을 민다.** 새 라운드 `## 0. vX.Y` 블록을 맨 위에 만들고 기존 `## 0.` → `## 0-0.`, `## 0-N.` → `## 0-(N+1)`. 본문 어딘가에 `§0-N` 참조가 하나 있다(회고 슬라이스 판) — 같이 +1 한다. 정규식으로 내림차순 치환하면 안전하다.
- **E-번호는 커밋 직전에 다시 grep한다.** 다른 사람이 같은 시간에 채번할 수 있다(9/9 E-104를 두 세션이 동시에 잡을 뻔했다). `grep -oE 'E-1[0-9]{2}' 01*.md | sort -V | tail -1`.
- **06 R 행 형식** — `| R번호 | 저장소 | **제목.** 본문(근거 · 처방 · 유지할 것) | 담당 | 근거(E · § · PR 리뷰) | ☐/✅ 상태 |`. 상태 칸에는 병합 해시 · 건수 · 건너뜀 0 · Deploy 결과를 적는다. 결정이 미뤄진 항목은 "무엇이 오면 닫히는가"를 적는다.
- **머리 메모는 최신이 위.** `> **vX.Y (날짜):** ...` 뒤에 `>` 빈 인용 줄.
- **05 §0 오류 코드표 문자열은 `CommonErrorCode`와 글자 단위로 같아야 한다**(코드 · 메시지 둘 다). 리뷰어가 대조한다.
- **계약 변경은 05가 먼저**이고 팀 채널에 `[계약변경]` 한 줄(07 §6).
- **`gh`는 해당 코드 저장소 디렉터리에서 실행한다.** docs 디렉터리에서 `gh pr edit 54`를 치면 docs 저장소의 PR을 찾다가 실패한다.
- **동시 작업.** 같은 체크아웃을 다른 세션 · 사람이 쓸 수 있다. 편집 전 `git status --porcelain`; 남의 로컬 커밋 위에 내 커밋을 얹어 push하면 그 커밋도 같이 올라간다 — 보고에 적는다. `git stash`는 공유 스택이라 쓰지 않는다.
- 편집은 앵커 문자열 `count == 1`을 assert하는 파이썬 스크립트로 하면 잘못된 자리에 들어가지 않는다.

## 7. 코드 쪽 함정

- **Spring Boot 4** — 3.x 예제를 옮기면 틀린다(AGENTS). Jackson 3 패키지, `HttpStatus.UNPROCESSABLE_CONTENT`.
- **E-98 체크섬 불일치** — `flyway repair`가 아니라 `down -v`. README "시작하기"에 판별법이 있다. 로컬 전용.
- **demo db가 5432를 선점**할 수 있다(`sottaejap-demo-db-1`, `restart: unless-stopped`). 9/9부터 demo 레포는 무시 대상이지만 컨테이너는 남아 있을 수 있다. §5의 55432로 피한다.
- **`@Async` 제안 이유 채우기**(#43)는 단위 테스트 밖 — 배선은 리허설에서 본다(§3-3). 거절 정책은 로그 후 버림(`config/AsyncConfig`).
- **XLSX 업로드**는 PR #63(`ef4c5d9`)부터 `XSSFWorkbook`을 부르기 **전에** 문턱 셋으로 센다(첫 장 20,030행 · 모든 장 200,000행 · 압축 푼 32MB). 그 전에는 시트를 통째로 올린 뒤 세서 힙이 작으면 400이 아니라 500이었다. 문턱을 만지면 `TransactionFileParser`의 선계수부터 본다 — 계약 문턱에 뒷장을 더하면 상한 안의 파일이 거절된다(01 E-111).
- **`gh pr checks --fail-level`은 이 gh 버전에 없다.** `gh pr checks <n>` 출력을 본다.
- 셸이 zsh면 `--include=*.java` 같은 glob은 `bash -c`로 감싼다.

## 8. 최근 결정의 맥락 (왜 그렇게 했나)

01의 E 행에 근거가 있지만, 한 줄로 알아야 리뷰가 빠르다.

| E | 한 줄 |
| --- | --- |
| E-100 | `Goal` · `User` · `Suggestion` · `BehaviorCluster`는 `@DynamicUpdate` — 전체 행 쓰기가 실적 배분을 덮었다(#36) |
| E-102 | `GET /analysis` highlight를 사용자별 한 칸 캐시, 키는 집계 그 자체. `fallback: true`는 캐시하지 않는다 → 가드레일 폴백이 매번 왕복(06 R33) |
| E-103 | 제안 `reason`을 AI 문장으로, 응답 경로 밖(`@Async`). 롤업된 리프는 상위 묶음 id로 찾는다(#43) |
| E-105 | 업로드 행수 상한 20,000 → 400 `TOO_MANY_ROWS`. client 타임아웃 분리 대신 server 한 곳(#52) |
| E-106 | FR-02-05 가맹점명 정규화 본선 제외 — `merchantNormalized`를 읽는 코드가 없다 |
| E-109 · E-110 | `recentMessages` 규격 밖 → 400(503 아님). 항목 상한 user 500 · assistant 2,000(#56 · #61) |
| E-111 | XLSX는 시트를 열기 전에 문턱 셋으로 센다(계약 = 첫 장 20,030행 · 메모리 = 모든 장 200,000행 · 압축 푼 32MB). 운영 힙 `-Xmx512m` · `mem_limit: 1g`은 `deploy/docker-compose.yml`에 명시(07 §12) — 힙을 줄이려는 것이 아니라 호스트 메모리에 딸려 다니지 않게 못 박는 것이다(#58) |
| E-112 | `/retrospects/chat` `message` 최대 500자 — #24 · #28과 같은 값. 단위는 `@Size`(UTF-16)로 둔다: UTF-16 단위 수 ≥ 코드 포인트 수라 더 엄격하고, ai `chat.py`의 `message`는 상한이 없어 맞출 계약이 없다. 통일은 세 DTO를 한 번에(#67 · PR #68 리뷰) |
| E-89 확정 | highlight 4종째 "이번 달 거래 중 돌아본 것이 아직 없어요" — 회고 시점/소비 시점 모호성 제거(#51). ai 안내문은 조건이 달라 그대로 복사하면 안 된다(ai #50) |

## 9. 물어볼 사람

| 무엇 | 누구 |
| --- | --- |
| 계약 변경 · 세 저장소 반영 확인 · PR 병합 | 통합 담당 고현석 (07 §6 · CONTRIBUTING §4) |
| ai 핸들러 · 프롬프트 · 가드레일 | ai 담당 (06 R19 · R23 · R32의 담당 칸) |
| client 배선 · 오류 메시지 표시 | client 담당 (06 R30 · R31) |
| 3사 카테고리 매핑표(R29) | 06 R29의 "매핑표(#11)에 묶인다" — 담당이 비어 있다. **먼저 정해야 한다** |
| 문구(highlight · reason) | 문구 담당 — PR #44 · #53 리뷰 스레드에서 확정한 이력 |
