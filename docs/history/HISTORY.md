# HISTORY

### [14] 2026-09-14 11:00 (KST) | 작업자: Agent
- 요청/목적: GitHub 첫 화면에서 README가 표시되도록 루트 README를 만들고 기존 작업 및 Codex 푸시 내역을 문서화
- 수행 내용:
  1) 하위 `CodexDesktop/README.md`만 있어 GitHub가 자동 표시하지 못하는 구조 확인
  2) 루트 `README.md`에 개요, 아키텍처, 기능, 작업 이력, 실행/빌드/테스트, 구조 및 GitHub 게시 기록 작성
  3) 초기 게시를 수행한 Codex 에이전트, 커밋(`c82a264`, `5eeef3e`) 및 56개 테스트 결과 명시
- 변경 파일: `README.md`, `docs/history/HISTORY.md`, `docs/memory/MEMORY_07.md`
- 검증:
  - `git diff --check` 통과, README 로컬 링크 3개 대상 존재 확인
  - `MEMORY_07.md` 353자(3,000자 미만)
  - Maven을 저장소 루트에서 처음 실행해 POM 부재로 실패; `-f CodexDesktop/pom.xml`로 재실행
  - Tests run: 56, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS
- 결과: 루트 README 작성 및 로컬 검증 완료, 푸시 준비
- 다음 액션: 테스트 후 `origin/main` 푸시
---

### [13] 2026-09-14 10:49 (KST) | 작업자: Agent
- 요청/목적: 현재 `devtest` 프로젝트를 GitHub `kbo3551/CodexCLI_App` 저장소의 `main` 브랜치에 게시
- 수행 내용:
  1) 상위의 다른 Git 저장소와 분리하기 위해 현재 폴더에 독립 Git 저장소를 초기화
  2) `origin`을 `https://github.com/kbo3551/CodexCLI_App.git`로 설정하고 원격이 빈 저장소임을 확인
  3) 기존 `.gitignore`에 따라 `CodexDesktop/target`, `CodexDesktop/dist` 등 빌드 산출물을 제외
  4) 커밋 후보에서 GitHub/OpenAI/AWS 토큰 및 개인 키 패턴을 점검(발견 없음)
- 변경 파일: `.git/`(로컬 저장소 메타데이터), `docs/history/HISTORY.md`, `docs/memory/MEMORY_06.md`
- 실행 명령: `git init -b main`, `git remote add origin ...`, `git ls-remote origin`, Maven 3.9.9 `mvn.cmd test`
- 검증:
  - 원격 참조 없음(빈 저장소)
  - 전체 테스트: Tests run: 56, Failures: 0, Errors: 0, Skipped: 0 / BUILD SUCCESS
  - 첫 푸시 후 로컬 HEAD와 `refs/heads/main` SHA 일치: `c82a264c831528bdd49978d8f1406e5f307c65f1`
  - 첫 `mvn test`는 PATH에 Maven이 없어 실패 후 프로젝트 빌드 스크립트와 동일한 Maven/JDK 경로로 재실행 성공
  - WSL Bash는 현재 환경의 CreateInstance 권한 거부로 실행 불가하여 PowerShell 사용
- 결과: 초기 커밋을 GitHub `origin/main`에 푸시하고 upstream 추적 설정 완료
- 다음 액션: 없음
---

### [12] 2026-09-11 13:00 (KST) | 작업자: Agent
- 요청/목적: 사이드바·사무실 등 패널 크기 조절 / 에이전트 사무실 퀄리티 대폭 향상
  (표정, 맥북, 사무실 꾸미기, 가운데 회의석)
- 수행 내용:
  1) 패널 크기 조절: BorderPane 고정 배치를 SplitPane 2단으로 교체.
     `rootSplit`[사이드바 | 센터], `centerSplit`[대화 | 사무실 | 변경사항].
     SplitPane 은 숨김 개념이 없어 패널 토글 시 item 을 add/remove 하고
     `addSplitItem` 이 패널 선호 폭에 맞춰 divider 위치를 잡음.
     divider 는 1px 헤어라인, hover 시 2px + accent 색 + h-resize 커서
  2) 사무실 레이아웃 재설계(16x14): 위/아래 책상 뱅크 8석 + **가운데 회의 테이블**(의자 4석),
     화이트보드, 정수기, 책장, 캐비닛(위에 머그컵), 화분, 벽시계, 액자, 러그.
     책상이 다 차면 남는 에이전트가 회의석에 앉도록 `seatFor(index)` 가 데스크→회의석→라운지 순으로 배정
  3) 캐릭터 고퀄리티화: **얼굴 추가**(눈·입), 상태별 표정(작업 중=집중한 눈,
     승인 대기=놀란 눈+든 손, 유휴=미소), 헤어 4종(짧은머리/긴머리/포니테일/번),
     안경(일부), 팔레트 8종, 걷기 4프레임, 셔츠 칼라, 사무용 의자(등받이+받침)
  4) **모니터 → 노트북**: 은색 힌지 본체 + 화면, 작업 중에는 화면 점등 + 텍스트 흐름 표현.
     회의 테이블에도 노트북/서류/머그 배치
  5) 창문에 하늘 그라데이션과 블라인드, 바닥 나뭇결, 러그 짜임 패턴 추가
- 부수 수정:
  - 착석 캐릭터가 책상·노트북과 겹쳐 보이던 문제: 좌석 y 를 내려 머리가 책상 아래로 오게 하고,
    점등 노트북을 캐릭터보다 **먼저** 그려 얼굴이 가려지지 않게 함
  - 천장 조명 광원 표현이 얼룩처럼 보여 제거
  - 도킹 패널에서 2배 배율이 안 나오던 문제: 맵이 16타일(256px)로 넓어져 512px 필요 →
    패널 최소 폭 530 / 기본 560 으로 상향. 분리 창은 3배가 정확히 들어가는 792x764
  - `AppSettings` 에 `withRuntime/withPolicy/withLanguage` 추가.
    필드가 늘어날 때마다 테스트의 위치 인자 생성자가 깨지던 문제를 없앰
- 변경 파일: `ui/MainWindow.java`, `ui/office/{OfficeMap,OfficeRenderer,AgentOfficeView}.java`,
  `model/AppSettings.java`, `resources/css/base.css`, 테스트 3종 수정
- 검증:
  - 전체 `mvn test` → Tests run: 56, Failures: 0, Errors: 0
    (`AgentWorkerTest` 를 새 배치에 맞춰 갱신: 책상 8, 회의석 4, 총 수용 12)
  - 프리뷰 스냅샷 `office-detached-ko.png`(3배, 분리 창) / `office-ko.png`(2배, 도킹 패널)로
    노트북·회의석·표정·소품 렌더 확인
  - `build-app.bat` exit=0 → exe 재생성, 실행 시 app-server 연결 및 thread 시작 확인
- 결과: 성공
- 미검증: divider 드래그 조작감(실행 후 직접 끌어봐야 확인)

### [11] 2026-09-11 11:25 (KST) | 작업자: Agent
- 요청/목적: `@` 로 파일이 안 잡힘 / CLI 의 검색 모드(All Results·Filesystem Only·Plugins) 대응
- 근본 원인(확정): JavaFX 는 TextInputControl 의 **textProperty 리스너를 캐럿 이동 전에** 발동시킴.
  첫 글자를 입력하면 text="@l" 인데 caret 은 아직 1 이라 조각이 빈 문자열 → null → 팝업 숨김.
  이후 caretPositionProperty 리스너가 발동하지만 `isShowing()` 조건으로 게이트되어 있어
  **팝업이 한 번도 열리지 않았음**
- 수행 내용:
  - 캐럿 리스너의 `isShowing()` 게이트 제거 → 캐럿이 정착한 뒤 항상 재평가
  - 조각 파싱을 `ui/MentionParser` 로 분리(순수 함수) 후 8건 테스트로 규칙 고정:
    캐럿이 텍스트보다 한 글자 뒤처진 경우, 이메일 주소 무시, 공백 경계, 캐럿 근처 토큰 선택,
    텍스트 범위 밖 캐럿 허용
  - `ComposerMentionWiringTest` 신규: 실제 Composer 를 씬에 올리고 scene graph 에서 입력 컨트롤을
    찾아 텍스트를 넣어 **텍스트→캐럿→조각→디바운스→검색** 전 구간이 실제로 호출되는지 검증.
    로그로 `caret=0 fragment=null` → `caret=4 fragment=log` → `searching log` → `1 matches` 확인
  - 검색 실패를 조용히 삼키던 `exceptionally(error -> null)` 에 debug 로깅 추가
  - 검색 모드 추가: `전체 결과` / `파일만` 을 ←/→ 로 전환, 푸터에 CLI 처럼 탭 표시.
    모드 전환은 이미 받아온 결과를 재필터링하므로 추가 요청 없음
- 부수 버그 수정: `debugLogging=true` 로 저장돼 있어도 **시작 시에는 logback 의 codex.wire 레벨을
  올리지 않아** 원본 프로토콜 로그가 안 남았음(설정을 다시 저장할 때만 적용). 시작 경로에
  `applyDebugLogLevel` 호출 추가하고 MainWindow 의 중복 구현을 제거
- 변경 파일: `ui/{Composer,MentionParser,MentionPopup}.java`, `CodexDesktopApplication.java`,
  `ui/MainWindow.java`, `resources/css/base.css`, `resources/i18n/messages*.properties`,
  `src/test/.../{MentionParserTest,ComposerMentionWiringTest}.java`
- 검증:
  - 전체 `mvn test` → Tests run: 56, Failures: 0, Errors: 0
  - `mention-ko.png` 로 모드 탭(전체 결과/파일만)과 힌트 확인
  - `build-app.bat` exit=0 → exe 재생성
- 결과: 성공. 사용자가 테스트한 이전 빌드에는 위 캐럿 버그가 있었고, 현재 빌드에서 해결됨
- 참고: SendKeys 로 앱에 키를 넣어 확인하려던 시도는 포커스가 닿지 않아 무의미했고,
  그 때문에 초기 진단이 지연됨. 이후에는 UI 배선도 헤드리스 테스트로 검증하는 방식으로 전환
- 범위 밖: CLI 의 Plugins 검색 소스는 대응 대상이 없어 모드에서 제외

### [10] 2026-09-11 10:46 (KST) | 작업자: Agent
- 요청/목적: CLI 의 `@` 파일 선택기처럼 표 형태(이름/경로/종류 + 하단 힌트)로 보이게 할 것
- 먼저 기능 자체 검증: `CodexFileSearchIntegrationTest` 신규 (실 app-server, 모델 호출 없음 → 쿼터 소모 0)
  → 프로젝트 루트에서 'Composer' 16건, 절대경로가 `/` 로 시작하고 상대경로로 끝남,
  'office' 에 디렉터리 히트 존재, 공백 쿼리는 요청조차 보내지 않음, 종료 후 프로세스 정리까지 확인.
  즉 검색 경로는 정상이었고 문제는 표현이었음
- 수행 내용:
  - `MentionPopup` 재작성: 선택 행 `›` 마커, 이름(고정폭·강조색, 폴더는 앰버색),
    흐린 상대 폴더 경로, 우측 정렬 `파일`/`폴더` 태그, 하단 힌트 `enter 삽입 · esc 닫기 · ↑↓ 이동`.
    이름 컬럼 폭을 결과 중 가장 긴 파일명으로 계산해 경로 컬럼이 한 줄로 정렬되게 함(들쭉날쭉 방지).
    행 높이 22px 로 줄여 9줄까지 표시
  - `SlashCommandPopup` 도 동일한 picker 레이아웃으로 통일 (마커/정렬/푸터)
  - CSS: 기존 slash 전용 규칙을 `.picker-*` 공용 규칙으로 교체
- 버그 발견/수정: 프로퍼티 파일에 개행 없이 append 해 `slash.helpTitle=...picker.hintInsert=...`
  가 한 줄로 붙어 두 키가 모두 깨졌음(멘션 푸터에 키 이름이 그대로 표시). 분리하고,
  `I18nTest` 에 재발 방지 3종 추가: 값에 '=' 가 있으면 실패(=붙음 감지), 두 번들 키 집합 동일성 검사
- 변경 파일: `ui/{MentionPopup,SlashCommandPopup}.java`, `resources/css/base.css`,
  `resources/i18n/messages*.properties`, `src/test/.../{CodexFileSearchIntegrationTest,I18nTest}.java`
- 검증:
  - 통합 테스트(실 Codex) 통과 — 위 항목
  - 전체 `mvn test` → Tests run: 46, Failures: 0, Errors: 0
  - `mention-ko.png` / `slash-ko.png` 스냅샷으로 컬럼 정렬·태그·푸터 번역 확인
  - `build-app.bat` exit=0 → exe 재생성, 실행 시 app-server 연결 및 thread 시작
- 결과: 성공
- 범위 밖으로 둔 것: CLI 의 `←/→ switch search modes`(All Results / Filesystem Only / Plugins) 는
  플러그인 검색 소스가 CLI 전용이라 구현하지 않음

### [9] 2026-09-11 10:25 (KST) | 작업자: Agent
- 요청/목적: CLI 처럼 `@` 로 파일을 찾아 참조하는 기능이 없음
- 조사: `codex app-server generate-ts` 로 타입 확인 →
  `FuzzyFileSearchParams { query, roots: string[], cancellationToken }`,
  결과 `{ root, path, match_type: file|directory, file_name, score, indices }`.
  `.protoref/probe6.py` 로 실제 호출 검증 → 쿼리당 약 150ms, root(절대경로)와 project-relative
  path 를 분리해서 돌려줌. 빈 쿼리는 0건
- 수행 내용:
  - `model/FileMatch` 신규 (root+path 로 절대경로 조립, 부모 경로 추출, 디렉터리 판별)
  - `CodexSessionService.searchFiles(query, root, limit)` 추가 (`fuzzyFileSearch`).
    Windows 에서 직접 트리를 훑지 않고 서버에 위임 — 이미 인덱싱돼 있고 ignore 규칙을 따르며
    WSL 안에서 검색하므로 멘션에 필요한 경로 형태가 그대로 나옴
  - `ui/MentionPopup` 신규: Popup+ListView, 파일명(고정폭) + 상대 경로(흐린 색).
    ContextMenu 를 쓰지 않은 이유는 슬래시 팔레트와 동일(포커스 탈취)
  - Composer: 캐럿 직전의 `@토큰` 감지(단어 시작에서만 → 이메일 주소는 무시),
    140ms 디바운스 + 시퀀스 번호로 늦게 도착한 응답 폐기, 위/아래·Enter·Tab·Esc 처리.
    선택 시 `@상대경로 ` 로 치환하고 멘션을 별도 맵에 기록.
    전송 시 텍스트에 아직 남아 있는 멘션만 `mention` UserInput 으로 포함(지우면 자동 제외)
  - 입력창 안내문에 `/ 명령, @ 파일` 표시
- 변경 파일: `model/FileMatch.java`(신규), `ui/MentionPopup.java`(신규),
  `ui/Composer.java`, `ui/MainWindow.java`, `codex/{CodexSessionService,CodexProtocol}.java`,
  `resources/css/base.css`, `resources/i18n/messages*.properties`,
  `src/test/.../FileMatchTest.java`(신규), `UiPreviewTest`
- 검증:
  - `FileMatchTest` 4건: 절대경로 조립, 디렉터리 판별, root 끝 슬래시, root 누락 대응
  - 전체 `mvn test` → Tests run: 44, Failures: 0, Errors: 0
  - `mention-ko.png` 스냅샷으로 파일명/경로 렌더 확인, `conversation-ko.png` 로 안내문 확인
  - `build-app.bat` exit=0 → exe 재생성, 실행 시 app-server 연결 및 thread 시작 확인
- 결과: 성공
- 미검증: 실제 타이핑으로 `@` 목록이 뜨고 선택되는 조작 (실행 후 직접 입력 필요)

### [8] 2026-09-11 09:05 (KST) | 작업자: Agent
- 요청/목적: 모델 선택이 하단에 반영 안 됨 / 추론 단계 조절 불가 / `/` 명령 없음 /
  사무실 별도 창이 작음 / 폰트가 얇아 가독성 나쁨 / 설정 페이지가 흰 배경
- 원인 규명(추측 배제):
  1) 추론 단계: `model/list` 실측 결과 항목이 `{"reasoningEffort":"low","description":...}` 인데
     코드가 `effort`/`id` 로 읽어 **효력 목록이 항상 빈 배열** → 서브메뉴가 안 만들어져 조절 불가.
     프로브(`.protoref/probe5.py`)로 실제 응답 확인 후 `reasoningEffort` 우선 파싱으로 수정
  2) 설정 흰 배경: 프리뷰 PNG 픽셀 측정으로 `#F4F4F4` 확인 → ScrollPane **viewport** 가 기본
     테마의 밝은 `-fx-background` 로 칠해지고 있었음. `.scroll-pane > .viewport` 전역 투명 처리
     (기존에는 sidebar/conversation 전용 선택자만 있었음)
  3) 사무실 별도 창: 창 900x760 에서 정수 배율이 2 로 떨어져 480px 로만 그려짐 →
     정확히 3배(720px)가 들어가는 744x836 으로 열고, 씬을 상하 중앙 정렬
- 수행 내용:
  - `ModelOption.from` 이 `reasoningEffort`/`effort`/`id` 순으로 읽도록 수정
  - `/` 슬래시 명령 팔레트 신규: `SlashCommand`, `SlashCommandPopup`(Popup+ListView).
    ContextMenu 대신 Popup 을 쓴 이유는 ContextMenu 가 키보드 포커스를 가져가 계속 타이핑하며
    좁혀갈 수 없기 때문. Composer 가 위/아래/Enter/Tab/Esc 를 전달.
    명령: /new /model /compact /diff /status /office /settings /stop /help
  - 인터페이스 폰트 설정 추가: `AppSettings.uiFont`, `FontLoader.availableUiFonts()` 로
    실제 설치된 후보만 노출(D2Coding/Pretendard/Segoe UI/Noto Sans KR/Malgun Gothic/나눔 계열…),
    루트 인라인 스타일로 적용. 코드·diff 는 자체 규칙으로 고정폭 유지
  - `StatusBar.showUsage()` 추가(/status 진입점)
- 변경 파일: `model/{AppSettings,ModelOption}.java`, `ui/{Composer,MainWindow,SettingsView,StatusBar,
  FontLoader,SlashCommand,SlashCommandPopup}.java`, `ui/office/OfficeRenderer.java`,
  `CodexDesktopApplication.java`, `resources/css/base.css`, `resources/i18n/messages*.properties`,
  테스트 3종 신규(`StylesheetTest`, `ModelOptionTest`, `CodexSessionSelectionTest`), `UiPreviewTest`
- 검증:
  - `StylesheetTest`: JavaFX `CssParser` 로 3개 스타일시트 파싱 → 오류 0, 규칙 수 정상,
    base.css 가 참조하는 모든 `-c-*` 토큰이 테마에 정의돼 있음을 검사
  - `ModelOptionTest`: 실제 응답 형태에서 low/medium/high/xhigh 파싱, 구형 문자열 배열 호환,
    선택이 ThreadConfig 에 반영되며 정책 필드는 불변
  - `CodexSessionSelectionTest`: `selectModel` 이 리스너에 새 모델/추론강도를 전달하는지 검증
    (하단 상태바가 갱신되지 않던 증상의 경로)
  - 프리뷰 스냅샷: `settings-ko.png`(다크 복구 + 폰트 설정 행), `office-detached-ko.png`(3배 꽉 찬 창),
    `slash-ko.png`(팔레트), `menu-ko.png`, `combo-ko.png`
  - 전체 `mvn test` → Tests run: 40, Failures: 0, Errors: 0
  - `build-app.bat` exit=0 → exe 생성, 실행 시 D2Coding 로드 + app-server 연결 + thread 시작,
    예외/CSS 경고 없음
- 결과: 성공
- 미검증: 슬래시 팔레트의 실제 키보드 조작, 폰트 변경 후 체감, 팝아웃 창 마우스 조작 (모두 실행 후 조작 필요)

### [7] 2026-09-10 17:30 (KST) | 작업자: Agent
- 요청/목적: 모델 선택 메뉴가 깨져 보임 (흰 배경 + 거의 안 보이는 글씨)
- 원인: ContextMenu/Menu/MenuItem 과 ComboBox 드롭다운은 **별도 popup 창**이라 앱 CSS 에
  전용 규칙이 없었고 기본 Modena(밝은 테마)로 그려졌음. 게다가 앞서 추가한 전역
  `.label { -fx-text-fill: -c-text; }` 가 메뉴 항목 라벨까지 흰색으로 칠해서
  흰 배경 + 흰 글씨가 됨. 콤보 드롭다운은 반대로 어두운 배경 + 어두운 글씨였음
- 수행 내용:
  - base.css 에 menus 섹션 추가: `.context-menu`(배경/테두리/라운드/패딩),
    `.menu-item`(+ `> .label` 색상, hover/focused/showing/disabled 상태),
    서브메뉴 화살표 `.menu > .right-container > .arrow`, radio/check 표시, 스크롤 화살표, 구분선
  - ComboBox 팝업은 셀이 `.combo-box` 의 자손이 아니라서 별도 선택자 필요 →
    `.combo-box-popup > .list-view > .virtual-flow > .clipped-container > .sheet > .list-cell`
    까지 명시해 배경/글자/hover/selected 지정
  - 검증 수단 확보: 팝업은 씬 스냅샷에 안 잡히므로 `UiPreviewTest` 에서 실제 ContextMenu 와
    ComboBox 팝업을 화면 밖(-3000,-3000, opacity 0) 임시 Stage 에 띄워 스냅샷 저장
    → `target/ui-preview/menu-ko.png`, `combo-ko.png`
- 변경 파일: `resources/css/base.css`, `src/test/.../UiPreviewTest.java`
- 검증:
  - menu-ko.png / combo-ko.png 로 다크 배경·가독 글씨·선택 강조·서브메뉴 화살표 확인
  - 전체 `mvn test` → Tests run: 31, Failures: 0, Errors: 0
  - `build-app.bat` exit=0 → `dist\CodexDesktop\CodexDesktop.exe` 재생성
- 결과: 성공
- 점검한 다른 팝업 표면: Tooltip/ScrollBar/CheckBox/TextField·TextArea 는 기존 규칙 있음,
  FileChooser·DirectoryChooser 는 OS 네이티브라 대상 아님

### [6] 2026-09-10 17:20 (KST) | 작업자: Agent
- 요청/목적: 사무실이 너무 작음 / 유휴 에이전트가 돌아다녀야 함 / 사무실을 별도 창으로 분리
- 수행 내용 / 원인:
  1) 사무실이 1배로 렌더되던 원인: HBox 가 자식을 minWidth 까지 줄일 수 있어 패널이 360px 로
     압축됨 → 15*16*2=480 이 안 되어 scale 1. 패널 minWidth 500 으로 올리고 채팅 컬럼에도
     minWidth 420 을 줘서 서로 밀어내지 못하게 함
  2) 확대 토글(⤢) 추가: 사무실이 센터 전체를 차지(채팅 숨김) ↔ 사이드 패널 복귀.
     렌더러가 정수 배율을 다시 계산하므로 늘어나지 않고 실제로 커짐
  3) 별도 창(↗) 추가: officeView 를 centerRow 에서 떼어 새 Stage 로 이동(스타일시트/폰트크기/아이콘
     승계), 창을 닫으면 원위치 도킹. 앱 종료 시 남은 창이 런타임을 붙잡지 않도록
     `MainWindow.closeAuxiliaryWindows()` 를 종료 경로에 연결
  4) 유휴 배회: AgentWorker 에 arrivalState/wanderCooldown 추가하고 `walkTo(x,y,onArrival)` 로
     정리. 일이 없는 에이전트는 쿨다운 만료 시 임의의 통로 타일로 걸어갔다가 자리로 복귀.
     turn 시작 시 쿨다운을 무한으로 올려 배회를 즉시 중단. `OfficeMap.wanderSpots()` 는 책상/좌석
     레인/벽/화분을 제외
  5) 패키징 안정화: 앱 종료 직후 런타임 DLL 잠금이 잠깐 남아 jpackage 가 실패하는 현상을 실제로
     겪어 `package-windows.cmd` 에 dist 삭제 5회 재시도 추가
- 변경 파일: `ui/office/{AgentOfficeView,AgentWorker,OfficeMap}.java`, `ui/MainWindow.java`,
  `CodexDesktopApplication.java`, `resources/i18n/messages*.properties`,
  `scripts/package-windows.cmd`, `src/test/.../AgentWorkerTest.java`(신규), `README.md`
- 검증:
  - `AgentWorkerTest` 8건 신규 (도착 포즈, 이동 방향, 프레임 필요 여부, 쿨다운 진행 조건,
    wanderSpots 가 책상/좌석 레인 제외, 책상 12개 각각 아래에 좌석 존재) → 통과
  - 전체 `mvn test` → Tests run: 31, Failures: 0, Errors: 0
  - 프리뷰 렌더로 배회 동작 확인(3초 시뮬레이션 후 유휴 에이전트가 통로에 분산, 활성 에이전트만 착석 타이핑)
  - `build-app.bat` → exit=0, `dist\CodexDesktop\CodexDesktop.exe` 생성
  - 새 exe 실행 → D2Coding 로드, `wsl.exe -d Ubuntu-24.04 --cd /mnt/c/.../DW -- bash -lc 'codex' 'app-server' '--stdio'`
    연결, thread 시작(gpt-5.6-sol), 예외 없음
- 결과: 성공
- 미검증: 별도 창의 실제 팝아웃/도킹 조작은 창을 띄워 클릭해야 확인 가능해 코드 경로만 검증함
- 다음 액션: WiX installer 검증

### [5] 2026-09-10 16:50 (KST) | 작업자: Agent
- 요청/목적: 실제 실행 화면 기준 UI/UX 결함 수정 (사무실에서 되돌아갈 수 없음, 설정 레이아웃 깨짐,
  입력창 양쪽 흰 점, 에이전트 이름 없음, 버튼 affordance 부족)
- 수행 내용 / 원인:
  1) 입력창 흰 점 4개: 프리뷰 PNG 픽셀을 직접 조사해 `#B6B6B7` 4픽셀이 텍스트 뷰포트 네 꼭짓점에
     있음을 확인. 원인은 Modena 의 레이어 배경(-fx-box-border)이 `.text-area` 규칙과 동일 특이도로
     뒤에 선언돼 이김. base.css 맨 끝에 `.composer-input` 계열 최종 오버라이드 추가
     (scroll-pane/viewport/content/corner 전부 배경·테두리 0) → 측정값 4 → 0
  2) 사무실 복귀 불가: 사무실이 centerStack 전체를 덮어 상단바(토글 버튼)까지 가렸음.
     centerRow 안의 오른쪽 패널(540px)로 변경 → 채팅은 왼쪽에 그대로, 상단바 유지, 패널에 × 버튼 추가,
     에이전트 클릭 시 스레드만 전환하고 패널은 유지
  3) 맵이 패널에서 1x 로만 렌더: 24x13 → 15x15 로 축소해 2x(480px) 확보, 씬을 상단 정렬
  4) 에이전트 식별 불가: 자리순으로 `에이전트 N` 고정 이름 + hover 시 스레드 제목, 하단에
     `착석 에이전트 N명 · 표시되지 않은 스레드 M개` 범례 추가
  5) 설정 깨짐: HBox 행마다 hint 가 남는 공간을 다르게 먹어 입력창 폭이 어긋나고 체크박스 문구가
     잘렸음. GridPane(라벨 140px / 컨트롤 가변) + hint 를 컨트롤 아래로 이동, 체크박스 wrapText,
     페이지 폭 780→640
  6) ToolCard 종류 라벨이 좁은 폭에서 `...` 로 축소되던 문제 → kindLabel minWidth=USE_PREF_SIZE
  7) 상단바 사무실/모델/컨텍스트 정리 버튼에 subtle-outline 적용
  8) 빌드 스크립트: 앱이 실행 중이면 런타임 DLL 잠금으로 jpackage 가 난해한 오류를 내던 문제 →
     `build-app.bat` 과 `package-windows.{cmd,sh}` 에 실행 중 감지/명확한 안내 추가
- 변경 파일: `resources/css/base.css`, `ui/MainWindow.java`, `ui/SettingsView.java`,
  `ui/conversation/ToolCard.java`, `ui/office/{AgentOfficeView,AgentWorker,OfficeMap,OfficeRenderer}.java`,
  `resources/i18n/messages*.properties`, `src/test/.../UiPreviewTest.java`,
  `build-app.bat`, `scripts/package-windows.{cmd,sh}`, `README.md`
- 검증:
  - 프리뷰 렌더 반복 검수(설정/대화/사무실) 및 흰 점 픽셀 카운트 4→0 측정
  - `mvn test` → Tests run: 23, Failures: 0, Errors: 0
  - `build-app.bat` 재실행 → `dist\CodexDesktop\CodexDesktop.exe` 생성, 실행 시 D2Coding 로드 및
    `wsl.exe -d Ubuntu-24.04 --cd /mnt/c/.../test -- bash -lc 'codex' 'app-server' '--stdio'` 연결 확인
  - 앱 실행 중 `build-app.bat` 재실행 → "CodexDesktop is still running" 안내 후 중단되는 것 확인
- 결과: 성공
- 다음 액션: WiX installer 검증 (미설치로 보류)

### [4] 2026-09-10 16:25 (KST) | 작업자: Agent
- 요청/목적: 더블클릭 한 번으로 앱을 뽑는 bat 파일
- 수행 내용:
  - `build-app.bat` (저장소 루트) 신규. 동작:
    1) JDK 21 자동 탐색 — `JAVA_HOME`(jpackage 존재 여부로 검증) →
       `%USERPROFILE%\devtools\jdk-21*` → Adoptium/Oracle/Microsoft/Corretto/Zulu/Liberica 설치 경로
    2) Maven 자동 탐색 — `MVN` → `%USERPROFILE%\devtools\apache-maven-*` → PATH
    3) `scripts\package-windows.cmd` 호출(중복 로직 없음) 후 결과 폴더를 explorer 로 열기
  - 인자 `installer` 지원, 실패 시 원인별 안내(WiX 미설치 등), 더블클릭 대비 `pause`
  - 스크립트 자동화용으로 `CODEXDESKTOP_NO_PAUSE=1` 시 pause 생략
- 변경 파일: `build-app.bat`(신규), `CodexDesktop/README.md`
- 검증:
  - `JAVA_HOME`/`MVN` 미설정 상태에서 실행 → JDK/Maven 자동 탐지, Tests run: 23 통과,
    `dist\CodexDesktop\CodexDesktop.exe` 93.4MB 생성, exit=0
  - `JAVA_HOME` 을 JDK 1.8 로 지정해도 jpackage 부재를 감지해 JDK 21 로 폴백하는 것 확인
- 결과: 성공
- 다음 액션: 없음 (WiX installer 검증은 여전히 보류)

### [3] 2026-09-10 16:15 (KST) | 작업자: Agent
- 요청/목적: 픽셀아트 "에이전트 사무실" 뷰 추가 (세션별 자리, 일을 시키면 자리로 걸어가서 작업)
- 수행 내용:
  - `ui/office/AgentWorker`: LOUNGE/WALKING/WORKING/WAITING/IDLE 상태 기계, 타일 좌표 보간 이동,
    L자 경로(코리도가 축 정렬이라 경로탐색 불필요)
  - `ui/office/OfficeMap`: 24x13 문자 레이아웃, `=` 런에서 워크스테이션 18개 자동 도출, 라운지 러그
  - `ui/office/OfficeRenderer`: Canvas 절차적 픽셀아트(16px 타일, 정수 배율 3x, 스프라이트/책상/모니터/
    화분/책장/캐비닛/창문), 라벨은 스케일 밖 화면 좌표로 그려 가독성 확보
  - `ui/office/AgentOfficeView`: CodexSessionListener 구현. turn/started → 자리로 걸어감,
    activity → 착석 타이핑 + 말풍선, 승인요청 → 머리 위 마커, turn/completed → 완료/중단/실패 표시.
    AnimationTimer 는 "보이는 동안 && 실제 움직임이 있을 때"만 동작. hover 시 이름, 클릭 시 스레드 열기
  - MainWindow: 상단바 사무실 토글, centerStack 에 추가, thread/list 결과로 자리 배치
  - i18n `office.*` (ko/en), CSS `.office-view`/`.office-canvas`/`.button.selected-toggle`
  - `UiPreviewTest` 에 office 렌더 추가 + `AgentOfficeView.renderFrameNow()` (펄스 없는 환경용)
- 변경 파일: `ui/office/*` (신규 4), `ui/MainWindow.java`, `resources/i18n/messages*.properties`,
  `resources/css/base.css`, `src/test/.../UiPreviewTest.java`, `README.md`
- 검증:
  - `mvn test` → Tests run: 23, Failures: 0, Errors: 0
  - `mvn test -Dgroups=preview` → `target/ui-preview/office-ko.png` 렌더 확인,
    3회 반복 검수하며 결함 수정: 배율 과소(맵 28x12→24x13), 모니터가 책상에서 떠 있음(책상면 위로 재배치),
    이름 라벨 겹침(활성/hover 만 표시), 바닥 좌우 재질 이음선 제거, 착석 스프라이트 확대
  - 패키징 재실행 → `dist/CodexDesktop/CodexDesktop.exe` 93.4MB
- 결과: 성공
- 다음 액션: WiX installer 검증, 장시간 대화 부하 측정

### [2] 2026-09-10 15:45 (KST) | 작업자: Agent
- 요청/목적: Phase 1~9 구현 (JavaFX 셸, WSL 연결, 스트리밍 채팅, Tool UI, 승인, Diff/Git,
  스레드, 설정/진단, jpackage) + 추가 요구사항(한국어 지원, 가독성 폰트, 파일/이미지 첨부,
  아이콘, CLI status 수준의 사용량 표시)
- 수행 내용:
  - Maven 프로젝트 생성: Java 21 / JavaFX 21.0.12 / Jackson 2.22.2 / commonmark 0.30.0 /
    SLF4J 2.0.19 + Logback 1.5.38 / JUnit 5.14.4 (Spring 미사용, Swing UI 미사용)
  - codex 계층: `CodexHost`/`WslCodexHost`(ProcessBuilder + wsl.exe, UTF-8, virtual thread
    stdout/stderr, stdin EOF → destroy → destroyForcibly 단계적 종료, WSL_UTF8=1),
    `CodexAppServerClient`(요청/응답 상관, 알림 팬아웃, 서버 요청 응답 보장),
    `CodexSessionService`(thread/turn 오케스트레이션, `UiEventPump`로 델타 배치 처리)
  - UI: 사이드바/대화/컴포저/상태바/설정/진단/변경사항 패널, ToolCard(지연 렌더),
    DiffView(라인 상한), ApprovalCard(inline), MarkdownRenderer(commonmark → JavaFX Node)
  - 첨부: 버튼/드래그앤드롭/Ctrl+V(이미지) → `localImage`/`mention` UserInput 으로 전송
  - 사용량: `thread/tokenUsage/updated`, `account/rateLimits/updated`, `account/rateLimits/read`
    → 상태바 요약 + UsagePopup(컨텍스트 %, 토큰 내역, 플랜, 기본/보조 한도, 초기화 시각)
  - 모델: `model/list` 기반 모델/추론강도 선택(turn/start override), `thread/compact/start`
  - i18n: `messages.properties` / `messages_ko.properties`, 언어 변경 시 셸 재생성
  - 폰트: 사용자가 제공한 D2Coding-Ver1.3.2 ttc 를 리소스로 번들, `Font.loadFonts` 로 4종 등록
  - 아이콘: `tools/GenerateIcons.java` 로 PNG 7종 + PNG 임베드 ICO 생성
  - 패키징: `scripts/package-windows.{sh,cmd}` → jpackage app-image/installer
- 변경 파일: `CodexDesktop/**` (신규 51 소스 + 리소스), `docs/PROTOCOL.md`,
  `docs/history/HISTORY.md`, `docs/memory/MEMORY_01.md`, `.gitignore`
- 검증:
  - `mvn test` → Tests run: 23, Failures: 0, Errors: 0
  - 통합 테스트(실 Codex): `mvn test -Dgroups=integration -Dsurefire.excludedGroups=none` 통과
    - host 실행 커맨드 `wsl.exe -d Ubuntu-24.04 --cd /mnt/c/... -- bash -lc 'codex' 'app-server' '--stdio'`
    - thread/start → model=gpt-6-astra approvals=on-request sandbox=readOnly
    - 스트리밍 items = userMessage, agentMessage, commandExecution, fileChange, agentMessage
    - fileChange 승인 요청 → accept → 실제 파일 수정 확인, turn diff 에 변경 내용 포함
    - 토큰 사용량 53,408 / 컨텍스트 21% 수신
    - `turn/interrupt` → status=interrupted, 이후 프로세스 종료 확인
  - 패키징: `dist/CodexDesktop/CodexDesktop.exe` (93.4MB, 런타임 포함) 실행 →
    D2Coding 4종 로드, ko 적용, app-server 연결, thread 시작, 종료 시 `stopped gracefully` + code=0
  - 고아 프로세스 점검: WSL 에 남은 app-server 는 사용자 기존 daemon(6h+ uptime)뿐, 앱 생성분 0
  - UI 검수: `mvn test -Dgroups=preview` 로 실제 위젯 스냅샷 렌더 → 결함 5건 수정
    (기본 Label 색상 불가시, git 문구 미번역, 이모지 글리프 미지원, 언어 옵션 미번역, 설정 좌측 정렬)
  - i18n 회귀: ResourceBundle 기본 로케일 폴백으로 English 선택 시 한국어가 나오던 버그 수정 + 테스트 추가
- 결과: 성공. Phase 0~9 완료, 실 Codex 대상 end-to-end 검증 완료
- 다음 액션: WiX 설치 후 installer 산출물 검증, 긴 대화(수백 턴) 부하 확인

### [1] 2026-09-10 13:20 (KST) | 작업자: Agent
- 요청/목적: CodexDesktop (Java 21 + JavaFX Windows GUI over WSL Codex CLI) Phase 0 환경/프로토콜 조사
- 수행 내용:
  - `wsl --list --verbose` → 배포판 `Ubuntu-24.04` (기본, WSL2, Running)
  - `codex --version` → `codex-cli 0.154.0`, 실행 경로 `/home/boryeong/.local/bin/codex`
    (실제 바이너리 `~/.codex/packages/standalone/releases/0.154.0-x86_64-unknown-linux-musl/bin/codex`)
  - `codex app-server --help` → `--stdio` 지원 확인, `generate-json-schema` / `generate-ts` 발견
  - `codex app-server generate-json-schema|generate-ts --experimental` 로 실제 프로토콜 스키마 추출
  - 실제 프로세스를 띄워 라이브 프로브 3종 실행 (추측 없이 관측):
    - probe.sh: initialize → initialized → getAuthStatus → thread/start
    - probe2.py: turn/start 스트리밍, item/*, commandExecution, turn/completed
    - probe3.py: fileChange 승인 요청 + accept, turn/diff/updated, turn/interrupt,
      thread/list(cwd 필터), thread/turns/list
  - 인증 상태: `{"authMethod":"chatgpt","requiresOpenaiAuth":true}` (기존 codex login 사용)
  - Windows 툴체인 점검: 기본 java=1.8.0_202(32bit), mvn/jpackage 없음
    → 관리자 권한 없이 `C:\Users\USER\devtools` 에 Temurin JDK 21.0.12.1 + Maven 3.9.9 포터블 설치
- 변경 파일:
  - `docs/PROTOCOL.md` (신규, 검증된 프로토콜 정리)
  - `docs/history/HISTORY.md` (신규)
  - `docs/memory/MEMORY_01.md` (신규)
  - `.protoref/` (신규, 프로토콜 참고자료 + 프로브 스크립트, git 제외 대상)
- 검증:
  - 실제 app-server 응답/알림 캡처 확인 (`/tmp/appserver2.jsonl`)
  - `java -version` → 21.0.12.1, `jpackage --version` → 21.0.12.1, `mvn -v` → 3.9.9
- 결과: 성공. 프로토콜 v2 메서드/이벤트/승인/인터럽트/히스토리 경로 전부 실측 확인
- 다음 액션: Phase 1 (Maven 프로젝트 + JavaFX 셸 UI)
