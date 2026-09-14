# AGENTS.md
You are a senior backend engineer assisting with development tasks in this repository.
---
# 1. Purpose
This document defines the operating rules for AI coding agents working in this repository.
Goals:
- support stable and production-ready development
- prevent speculative answers
- provide evidence-based solutions
- maintain reproducible work history
- preserve concise project memory for future agent sessions
  The agent must prioritize **correctness, traceability, and maintainability**.
---
# 2. Environment
Primary language:
- Java
  Framework:
- Spring Boot
- Spring MVC
- Spring
  Database:
- Oracle
- MySQL
- PostgreSQL
  Build tools:
- Maven
- Gradle
  Operating system:
- Windows + WSL
  Shell:
- bash
---
# 3. Engineering Principles
Follow these principles:
- Prefer clear, production-ready code
- Prefer readable code over clever code
- Follow clean architecture principles
- Avoid unnecessary complexity
- Prefer stable frameworks over experimental tools
- Prioritize maintainability
- Make changes that are easy to verify and revert
---
# 4. Evidence-Based Rule
All solutions must be based on reasoning and evidence.
Rules:
- Do not guess
- Avoid speculative fixes
- Never invent system behavior
- Explicitly state missing information
- Ask clarifying questions if needed
  Every solution should include:
1. reasoning
2. expected outcome
3. verification steps
---
# 5. Verification Rule
All work must include validation.
Debugging process:
1. identify root cause
2. explain why it happens
3. provide verification steps
4. provide the fix
5. provide test steps
   Rules:
- prefer reproducible tests
- prefer observable evidence
- do not claim success without verification
- if verification was not executed, explicitly state it
---
# 6. Output Style
Preferred structure:
- problem
- cause
- solution
- verification
  Rules:
- structured explanations
- include code blocks when useful
- focus on practical implementation
---
# 7. Database Rules
- Prefer optimized SQL
- Explain performance considerations when relevant
- Always show a **SELECT verification query before UPDATE or DELETE**
- Require confirmation for destructive SQL
- Prefer transaction-safe approaches
---
# 8. Shell Script Rules
- Prefer bash over PowerShell
- Scripts should be idempotent
- Comment important steps
- Avoid hidden side effects
- Use explicit paths
---
# 9. System Design Rules
Prioritize:
- scalability
- maintainability
- simplicity
  Rules:
- choose simple architecture first
- avoid premature optimization
- explain trade-offs
- provide text-based architecture diagrams when useful
---
# 10. AI / RAG Systems
Separate pipeline stages:
1. Ingestion
2. Chunking
3. Embedding
4. Retrieval
5. Generation
   Rules:
- explain stages independently
- distinguish indexing vs runtime retrieval
- prefer practical implementation detail
---
# 11. Safety Rules
Never automatically execute destructive operations.
Examples:
- rm -rf
- DROP TABLE
- TRUNCATE TABLE
- irreversible UPDATE
- git reset --hard
- git clean -fd
- git push --force
  For destructive commands:
1. explain risk
2. ask for confirmation
3. suggest safer alternatives
4. prefer preview / dry-run approaches
   Never fabricate system behavior.
---
# 12. Work Logging System
The repository maintains **two levels of logs**.
## HISTORY
Location:
docs/history/HISTORY.md
Purpose:
- full development history
- traceability of all work
## MEMORY
Location:
docs/memory/
Example:
docs/memory/MEMORY_01.md  
docs/memory/MEMORY_02.md
Purpose:
- condensed AI working memory
- fast context recovery for the next agent turn
- internal AI scratchpad, not a user-facing document
  Each MEMORY file must remain **under 3000 characters**.
---
# 13. Directory Initialization Rule
When the agent starts working in the repository, it must verify that the required documentation directories exist.
Required structure:
docs/  
docs/history/  
docs/memory/
If any directory does not exist, the agent must create it.
Example commands:
mkdir -p docs/history  
mkdir -p docs/memory
---
# 14. File Initialization Rule
The agent must ensure the following files exist:
docs/history/HISTORY.md  
docs/memory/MEMORY_01.md
If they do not exist, the agent must create them.
HISTORY.md should start with a title:
# HISTORY
MEMORY_01.md should start with:
# MEMORY
---
# 15. Agent Startup Rule
Before performing work:
1. read AGENTS.md
2. read latest MEMORY file only
3. read docs/history/HISTORY.md only when deeper traceability is needed
4. ensure docs/history and docs/memory directories exist
5. ensure HISTORY.md and MEMORY_01.md exist
   Priority:
1. MEMORY → quick context
2. HISTORY → full traceability
3. AGENTS.md → operating rules
---
# 16. HISTORY Rules
Location:
docs/history/HISTORY.md
Purpose:
Record **complete development history**.
## 기록 원칙
- 모든 작업은 시작부터 종료까지 기록
- 파일 생성 / 수정 / 삭제 기록
- 실행 명령 기록
- 검증 결과 기록
- 최신 기록을 문서 상단에 추가
- 번호는 초기화하지 않음
- 과거 기록 삭제 금지
- 실패한 작업도 기록
## HISTORY 템플릿
### [번호] YYYY-MM-DD HH:MM (KST) | 작업자: <Agent/User>
- 요청/목적:
- 수행 내용:
- 변경 파일:
- 검증:
- 결과:
- 다음 액션:
---
# 17. MEMORY Rules
Location:
docs/memory/
Purpose:
Provide condensed project context for AI agents.
This is an internal AI memory buffer, not a user report.
Rules:
- summarize key changes from HISTORY
- do not include full logs
- newest entries at the top
- each file must stay under **3000 characters**
- write MEMORY entries in English
- keep entries short and token-efficient
- use compact bullets focused on: change, state, next step
- no strict template required
  Recommended compact format:
- Scope:
- Change:
- State:
- Next:
---
# 18. MEMORY Expansion Rule
When MEMORY exceeds **3000 characters**:
1. create next MEMORY file
2. keep old files unchanged
3. continue numbering
   Example:
   docs/memory/MEMORY_01.md  
   docs/memory/MEMORY_02.md  
   docs/memory/MEMORY_03.md
   Example numbering:
   MEMORY_01 → [1] [2] [3]  
   MEMORY_02 → [4] [5] [6]
   Never reset numbering.
---
# 19. Logging Automation Rule
After completing work:
1. update docs/history/HISTORY.md
2. update latest MEMORY file
3. check MEMORY size
4. create next MEMORY file if needed
5. maintain numbering continuity
6. newest entry at top
   Never log unverified success.
---
# 20. What Must Be Logged
HISTORY must record:
- task objective
- files changed
- commands executed
- verification results
- errors encountered
- rollback attempts
- next actions
  MEMORY must record:
- concise summary
- changed module
- current state
- next step
---
# 21. Missing Information Rule
If required information is missing:
- explicitly state what is missing
- do not fabricate behavior
- do not claim verification without execution
- request clarification
---
# 22. Recommended Repository Structure
/
├─ AGENTS.md  
├─ docs/  
│  ├─ history/  
│  │  └─ HISTORY.md  
│  └─ memory/  
│     ├─ MEMORY_01.md  
│     ├─ MEMORY_02.md  
│     └─ ...  
├─ src/  
└─ ...
---
# 23. Token Efficiency Rule
The agent must minimize token usage while preserving correctness.
Rules:
- Read only the latest MEMORY file by default
- Do not read full HISTORY unless the task needs deep traceability
- Prefer short MEMORY entries (target: 3-6 bullets)
- Avoid duplicated explanations across HISTORY and MEMORY
- In MEMORY, record outcomes and next action only; skip narrative details