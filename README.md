# CodexDesktop

Windows에서 WSL에 설치된 Codex CLI를 데스크톱 GUI로 사용할 수 있게 만든 JavaFX 애플리케이션입니다.

별도의 OpenAI API 키를 사용하지 않습니다. Windows 애플리케이션이 `wsl.exe`를 통해
`codex app-server --stdio`를 실행하고 JSON-RPC로 통신하므로, WSL에 이미 설정된 Codex 로그인,
`~/.codex/config.toml`, 샌드박스 및 승인 정책을 그대로 사용합니다.

## 프로젝트 상태

| 항목 | 내용 |
|---|---|
| 애플리케이션 버전 | `0.1.0` |
| 런타임 | Java 21, JavaFX 21.0.12 |
| 대상 환경 | Windows 10/11 + WSL 2 |
| 검증된 Codex CLI | `0.154.0` / Ubuntu 24.04 |
| 자동 테스트 | 56개 통과, 실패 0개 |
| 기본 브랜치 | `main` |

## 동작 구조

```text
Windows 10/11
└─ CodexDesktop.exe                 Java 21 + JavaFX
      │
      │ stdin/stdout, line-delimited JSON-RPC
      ▼
   wsl.exe -d <distro> --cd <project> -- codex app-server --stdio
      └─ 기존 Codex 로그인, 설정, 승인 정책, Git 작업 환경 사용
```

## 주요 기능

- 프로젝트별 Codex 스레드 생성, 목록 조회 및 재개
- Assistant 응답 스트리밍과 CommonMark 기반 Markdown 렌더링
- 명령 실행, 파일 변경, 추론 과정 등 Tool UI 표시
- 승인 요청에 대한 허용, 세션 허용 및 거부 처리
- 변경 파일 Diff 패널과 Git 브랜치·변경 수 표시
- 모델과 추론 강도 선택, 컨텍스트 압축
- `/` 명령 팔레트: 새 스레드, 모델, 압축, Diff, 상태, 사무실, 설정, 중지
- `@` 파일 검색과 Composer 파일 참조 삽입
- 버튼, 드래그 앤 드롭, 붙여넣기를 통한 파일·이미지 첨부
- 한국어·영어 UI, 다크·라이트 테마, 글자 크기 설정
- WSL/Codex/Git 상태 진단과 사용량 표시
- 모든 주요 패널의 드래그 크기 조절

## 에이전트 사무실

각 Codex 스레드를 픽셀 아트 캐릭터로 보여 주는 실시간 사무실 화면을 제공합니다.

- 작업 시작 시 캐릭터가 자리로 이동하고 작업 중에는 노트북을 사용
- 승인 대기, 작업 중, 완료 및 유휴 상태에 따른 표정·동작 표현
- 8개 일반 책상과 4개 회의석, 최대 12개 에이전트 표시
- 유휴 캐릭터 이동과 스레드별 고정 외형
- 대화 옆 도킹, 전체 화면 및 별도 창 모드
- 화이트보드, 정수기, 책장, 식물, 시계, 러그 등 사무실 소품
- 화면에 보이고 움직임이 있을 때만 애니메이션 루프 실행

## 구현 및 개선 이력

2026년 9월 10일부터 11일까지 Codex 에이전트와 함께 다음 작업을 단계적으로 구현하고
실행 화면 및 자동 테스트로 검증했습니다.

1. Codex app-server 프로토콜 조사
   - 초기화, 스레드, 턴, 스트리밍 이벤트, 승인, 인터럽트 및 히스토리 동작 실측
   - 조사 결과를 [`docs/PROTOCOL.md`](docs/PROTOCOL.md)에 기록
2. JavaFX 애플리케이션 기반 구현
   - 프로젝트·스레드 사이드바, 스트리밍 대화, Tool 카드, 승인 UI, Diff/Git, 설정 및 진단
   - 실제 Codex app-server를 이용한 통합 테스트 구성
3. Windows 패키징
   - `build-app.bat` 더블클릭 빌드
   - Java 런타임을 포함하는 app-image 및 WiX 설치 파일 빌드 지원
4. 픽셀 아트 에이전트 사무실
   - 스레드별 자리, 작업 상태 애니메이션, 별도 창·전체 화면·도킹 모드 구현
   - 캐릭터 외형, 회의석, 노트북, 가구와 배경 표현 개선
5. UI/UX 안정화
   - 설정 레이아웃, 모델 선택 메뉴, 사무실 복귀 및 창 크기 문제 수정
   - 사이드바·대화·사무실·변경사항 영역을 `SplitPane` 기반 가변 패널로 변경
6. 모델 및 명령 기능
   - 모델 선택 결과 반영, 추론 강도 선택, 컨텍스트 압축과 `/` 명령 팔레트 구현
7. `@` 파일 선택기
   - fuzzy file search, 표 형태 검색 결과, 키보드 탐색 및 파일 참조 삽입 구현
   - JavaFX 캐럿 갱신 순서로 첫 검색이 열리지 않던 문제를 재현 테스트와 함께 수정
8. 품질 검증
   - 순수 단위 테스트와 실제 JavaFX Scene을 사용하는 연결 테스트 포함 총 56개 테스트 통과

전체 변경 내역과 각 작업의 검증 결과는
[`docs/history/HISTORY.md`](docs/history/HISTORY.md)에서 확인할 수 있습니다.

## 실행 및 빌드

### 요구 사항

- Windows 10/11 및 WSL 2
- WSL 배포판에 설치·로그인된 Codex CLI
- 소스 실행/빌드 시 JDK 21 및 Maven 3.9 이상

### 소스에서 실행

```powershell
cd CodexDesktop
mvn clean javafx:run
```

### Windows 애플리케이션 빌드

저장소 루트에서 `build-app.bat`를 더블클릭하거나 다음 명령을 실행합니다.

```powershell
.\build-app.bat
```

설치 파일을 만들려면 WiX Toolset이 필요합니다.

```powershell
.\build-app.bat installer
```

기본 결과물은 `CodexDesktop/dist/CodexDesktop/`에 생성됩니다. 애플리케이션을 다른 위치로
옮길 때는 번들 Java 런타임이 포함된 `CodexDesktop` 폴더 전체를 복사해야 합니다.

### 테스트

```powershell
cd CodexDesktop
mvn test
```

```powershell
# 실제 Codex를 사용하는 통합 테스트
mvn test -Dgroups=integration -Dsurefire.excludedGroups=none

# UI 프리뷰 렌더링 테스트
mvn test -Dgroups=preview -Dsurefire.excludedGroups=none
```

## 저장소 구조

```text
.
├─ README.md
├─ build-app.bat
├─ CodexDesktop/
│  ├─ pom.xml
│  ├─ packaging/
│  ├─ scripts/
│  └─ src/
│     ├─ main/java/com/codexdesktop/
│     │  ├─ codex/       # app-server 프로토콜과 세션
│     │  ├─ model/       # 설정, 프로젝트, 스레드, 첨부 모델
│     │  ├─ service/     # WSL, Git, 진단, 영속화
│     │  └─ ui/          # JavaFX 화면과 컴포넌트
│     └─ test/           # 단위·통합·UI 테스트
└─ docs/
   ├─ PROTOCOL.md
   ├─ history/HISTORY.md
   └─ memory/
```

`target/`, `dist/` 등 로컬 빌드 산출물은 `.gitignore`로 저장소에서 제외됩니다.

## GitHub 게시 기록

이 저장소의 초기 Git 설정, 커밋, 테스트 및 GitHub 푸시는 2026-09-14에 Codex 에이전트가
사용자의 요청에 따라 수행했습니다.

- 대상: [`kbo3551/CodexCLI_App`](https://github.com/kbo3551/CodexCLI_App)
- 초기 소스 커밋: `c82a264` (`Initial commit: CodexDesktop`)
- 게시 기록 커밋: `5eeef3e` (`docs: record GitHub publication`)
- 게시 전 비밀정보 패턴 검사: 발견 없음
- 게시 전 검증: Tests run 56, Failures 0, Errors 0, Skipped 0
- 푸시 후 검증: 로컬 HEAD와 GitHub `refs/heads/main` SHA 일치 확인

애플리케이션의 상세 사용법은 [`CodexDesktop/README.md`](CodexDesktop/README.md)도 참고하세요.
