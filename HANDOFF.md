# sottaejap-server 인수인계 (2026-09-09 저녁)

이 문서를 읽으면 server 담당이 client 작업으로 옮겨 간 뒤 **남은 server 작업을 어디서부터 이어받을지**,
**한 건을 정본 → 이슈 → PR → 리뷰 회신 → 06 반영까지 어떻게 끝내는지**, **검증을 어떻게 돌리고 무엇에
걸리는지**를 안다. 규칙 본문은 반복하지 않는다 — [AGENTS.md](./AGENTS.md) · [CONTRIBUTING.md](./CONTRIBUTING.md) ·
[README.md](./README.md) · `sottaejap-docs/AGENTS.md`가 정본이고 여기에는 **그 문서에 없는 실전 지식**만 적는다.

기준 시각: 2026-09-09 17:55 KST. 상태 표는 그 순간의 스냅샷이다 — **최신은 항상 06 액션시트와 GitHub**다.

## 1. 읽는 순서

1. [AGENTS.md](./AGENTS.md) — 모르면 틀리는 전제 5개 (Spring Boot 4 · 규칙 엔진 결정론 · AI 호출 두 곳 · 회고 저장 트랜잭션 · 월간 리포트 확정).
2. [CONTRIBUTING.md](./CONTRIBUTING.md) — 브랜치 · 커밋 · PR · 검사 명령.
3. `sottaejap-docs/06_액션시트.md` 머리의 최신 메모 5~6개와 `R-번호` 표의 ☐ 행.
4. 이 문서 §2 → §3.

## 2. 지금 상태

| 항목 | 값 |
| --- | --- |
| `main` | `a8ca135` — PR #59(#56 `recentMessages` 규격 밖 → 400) 병합. CI · Deploy success |
| 배포 | `main` 병합 → CI 통과 → `deploy.yml`이 EC2로 자동 배포. **EC2는 프리티어**(README 배포 문단) |
| 테스트 | 전체 빌드 540건대, DB 통합 포함. CI는 `RUN_DB_INTEGRATION_TESTS=true`로 돈다 |
| 열린 PR | **#62**(#61 `recentMessages` role별 상한, CI pass) · **#63**(#58 XLSX 스트리밍 카운트 + 힙 320m, CI pass) — 둘 다 리뷰 대기 |
| 열린 이슈 | #49 · #57 · #58(→ PR #63) · #60 · #61(→ PR #62) · #64(이 문서) |
| docs | 01 v2.33 · 05 v2.34 · 06 v2.47. 06이 정본 진행표다 |

오늘(9/9) 병합된 것: #42(#36) · #44(#20) · #45(#19) · #46(#43) · #48(#47) · #53(#51) · #54(#52) · #55(#50) · #59(#56).
전부 06에 병합 해시 · Deploy 결과까지 적혀 있다.

## 3. 남은 작업과 다음 한 걸음

### 3-1. 결정 없이 지금 할 수 있는 것

| 이슈 | 다음 한 걸음 | 크기 |
| --- | --- | --- |
| **#57** `PendingSummary` javadoc | `rules/aggregate/PendingSummary.java` 주석 한 단락 — "`ANALYSIS_NARRATE`의 `state`에는 싣지 않는다(E-75). 내부 AI Tool 응답(`InternalAnalysisResponse`)에는 싣는다(ai #50)". 코드 변경 없음, PR 하나 | 10분 |
| **#60** `/retrospects/chat` `message` 상한 없음 | `RetrospectChatRequest.message`에 `@Size(max = 500)` — `POST /chat/finance` · `/chat/analysis`와 같은 값. **05 §2 #11에 먼저 한 줄**(1~500자), 01 E-번호 채번, 검증 테스트 1건. PR #62와 같은 파일을 건드릴 수 있으니 #62 병합 뒤에 | 30분 |
| **PR #62 · #63 리뷰** | 작성자가 같은 사람이라 리뷰는 통합 담당(고현석) 또는 이어받는 사람이 한다. 리뷰 방식은 §4-7 | — |

### 3-2. 입력이 있어야 시작되는 것

| 항목 | 막힌 곳 | 입력이 오면 |
| --- | --- | --- |
| **R29** 카테고리 매핑 (P0 · FR-02-03) | **3사 원본 카테고리 값 목록이 어디에도 없다.** server · docs에 CSV 표본 0건 | 04 §4 "내부 통합 카테고리(초안)" 10종에 대응표 초안을 04에 먼저 올리고 확인 → `TransactionServiceImpl.category()` 변환 + **V10 백필 마이그레이션 1회**(`category` ← `source_category` 매핑) + **전체 묶음 재계산 1회**(E-58 — `clusterKey`의 카테고리 · 시간대 자리가 같이 움직인다) + 05 §2 `category`를 enum으로 되돌림(05 v2.21 취소). 06 R29 · 04 v2.15 |
| **#49 / R33** highlight 캐시와 가드레일 폴백 | 리허설에서 `fallback: true` 비율 | A(폴백도 캐시) / B(원인 필드 계약 변경, 05 먼저) / C(그대로) 중 하나를 01에 E-번호로. 이슈 본문에 셋의 장단이 있다 |
| **#58** 운영 힙 | PR #63이 올라와 있다(스트리밍 카운트 + `-Xmx320m`) | 리뷰 · 병합. 프리티어 1GB에서 db · ai · server 셋이 같이 뜨는 것을 EC2 `docker stats`로 한 번 본다 |

### 3-3. EC2 리허설에서 볼 것 (사람 접근 필요)

배포본으로 한 번씩 확인하고 06에 결과를 적는다. 하나라도 어긋나면 이슈로.

- 회고 저장 직후 `GET /suggestions`의 `reason`이 템플릿 → 다음 조회에서 AI 문장으로 바뀌는가 (`@Async` 배선, 06 v2.36 메모 — 단위 테스트 밖).
- `GET /analysis` 두 번째 진입이 `ANALYSIS_NARRATE`를 다시 부르지 않는가 (E-102 캐시, `OPENAI_API_KEY` 있어야 체감).
- `fallback: true` 비율 — #49 입력.
- 20,001행 CSV → 400 `TOO_MANY_ROWS` · 3MB xlsx → 400(OOM이면 500, #58).
- `POST /chat/analysis` 왕복(1.0~1.5초, 06 v2.46) · `recentMessages`에 `role: system` → 400.

### 3-4. 짝 이슈 — server가 지켜볼 것

다른 저장소 몫이지만 server 계약과 물려 있다. 그쪽이 닫히면 05 · 06의 해당 줄을 갱신한다.

| 저장소 | 이슈 | server와 물린 곳 |
| --- | --- | --- |
| client | #24 | 400 `TOO_MANY_ROWS`의 `message`를 화면이 버린다 — 05 §2 v2.31의 ⚠️ 줄 |
| client | #22 · #15 · #16 | `/chat/analysis` 배선 · 거래 목록 배선(R30) · 표준 태그 라벨/값 분리(R31) |
| ai | #55 | assistant `reply` 2,000자 보장 — server E-110의 짝 |
| ai | #48 | `ANALYSIS` 숫자 가드 — server #49 결정의 입력 |

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
- **XLSX 업로드**는 PR #63 전까지 시트를 통째로 올린 뒤 행을 센다. 힙이 작으면 400이 아니라 500.
- **`gh pr checks --fail-level`은 이 gh 버전에 없다.** `gh pr checks <n>` 출력을 본다.
- 셸이 zsh면 `--include=*.java` 같은 glob은 `bash -c`로 감싼다.

## 8. 최근 결정의 맥락 (왜 그렇게 했나)

01의 E 행에 근거가 있지만, 한 줄로 알아야 리뷰가 빠르다.

| E | 한 줄 |
| --- | --- |
| E-100 | `Goal` · `User` · `Suggestion` · `BehaviorCluster`는 `@DynamicUpdate` — 전체 행 쓰기가 실적 배분을 덮었다(#36) |
| E-102 | `GET /analysis` highlight를 사용자별 한 칸 캐시, 키는 집계 그 자체. `fallback: true`는 캐시하지 않는다 → 가드레일 폴백이 매번 왕복(#49) |
| E-103 | 제안 `reason`을 AI 문장으로, 응답 경로 밖(`@Async`). 롤업된 리프는 상위 묶음 id로 찾는다(#43) |
| E-105 | 업로드 행수 상한 20,000 → 400 `TOO_MANY_ROWS`. client 타임아웃 분리 대신 server 한 곳(#52) |
| E-106 | FR-02-05 가맹점명 정규화 본선 제외 — `merchantNormalized`를 읽는 코드가 없다 |
| E-109 · E-110 | `recentMessages` 규격 밖 → 400(503 아님). 항목 상한 user 500 · assistant 2,000(#56 · #61) |
| E-89 확정 | highlight 4종째 "이번 달 거래 중 돌아본 것이 아직 없어요" — 회고 시점/소비 시점 모호성 제거(#51). ai 안내문은 조건이 달라 그대로 복사하면 안 된다(ai #50) |

## 9. 물어볼 사람

| 무엇 | 누구 |
| --- | --- |
| 계약 변경 · 세 저장소 반영 확인 · PR 병합 | 통합 담당 고현석 (07 §6 · CONTRIBUTING §4) |
| ai 핸들러 · 프롬프트 · 가드레일 | ai 담당 (06 R19 · R23 · R32의 담당 칸) |
| client 배선 · 오류 메시지 표시 | client 담당 (06 R30 · R31) |
| 3사 카테고리 매핑표(R29) | 06 R29의 "매핑표(#11)에 묶인다" — 담당이 비어 있다. **먼저 정해야 한다** |
| 문구(highlight · reason) | 문구 담당 — PR #44 · #53 리뷰 스레드에서 확정한 이력 |
