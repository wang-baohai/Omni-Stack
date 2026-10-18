# 공유 AI 엔지니어링 자산

`ai/`가 기준 원본입니다. 루트 [AGENTS](../AGENTS.md)와 [기술 사실](../docs/architecture.kr.md)이 제약과 현재 동작을 정의합니다. [매니페스트](manifest.yaml)에 출처, 버전, 라이선스, 의존성, 도구를 기록합니다.

## 내용

- `skills/`: 기존 grill-me 및 프로젝트 변경 흐름 omni-change.
- `prompts/`: 변경 검토와 문서 동기화 템플릿.
- `knowledge/`: 출처, 날짜, 무효화 조건이 있는 경험이며 또 다른 계약이 아님.
- `adapters/`: Codex/Qoder 기본 검색 위치를 생성하고 사용자 편집 덮어쓰기를 방지.

자체 유지보수 플러그인 소스는 발견하지 못했습니다. 외부 도구는 공식 출처만 등록하고 설치 캐시는 복사하지 않습니다. 2026-10-12 Docker의 읽기 전용 Git 검사로 grill-me 파일 전체 SHA-256이 [고정 상위 커밋](https://github.com/mattpocock/skills/blob/694fa30311e02c2639942308513555e61ee84a6f/skills/productivity/grill-me/SKILL.md)과 일치함을 확인하고 해당 [MIT 라이선스](skills/grill-me/LICENSE)를 보존했습니다. 과거 도입 과정은 독립적으로 증명하지 않았습니다. 개인 전역 플러그인, 계정, 메모리는 포함하지 않습니다.

## 설치와 확인

저장소 루트에서 컨테이너 도구를 빌드하고 CLI 의존성을 설치합니다.

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --check
```

미리보기는 쓰지 않습니다. `--apply`는 `.agents/skills/<name>/SKILL.md` 또는 `.qoder/skills/<name>/SKILL.md`를 생성하며 로컬 편집을 보호합니다. 원본 변경 후 다시 설치하고 생성 파일은 직접 편집하지 않습니다. 검색을 위해 작업 공간 재로딩 또는 새 턴이 필요할 수 있습니다. 디렉터리 검사만으로 실제 도구 로딩을 증명할 수는 없습니다.

[2026-10-18 네이티브 검색 검증](../docs/reference/evidence/qoder-native-skill-discovery-2026-10-18.json)에서는 격리 Docker 컨테이너의 공식 Qoder CLI(npm 호환 배포판) `1.1.73`으로 생성 후 이동한 다섯 프리셋에서 `qodercli skills list`를 실행했습니다. `grill-me`와 `omni-change`가 모두 `Enabled`였고, 이름과 설명은 정식 소스와 일치하며 위치는 각 프로젝트의 파생 진입점을 가리켰습니다. 프리셋마다 새로운 개별 설정을 사용하고 네트워크를 차단했습니다. 계정이나 전역 상태를 복사하지 않았으며 모델 요청도 보내지 않았습니다. 검증 범위는 CLI 프로젝트 Skill 검색이며 IDE 화면이나 Skill 작업 실행은 포함하지 않습니다.

## 유지보수와 프리셋

기준 원본만 변경하고 매니페스트 버전과 파생 파일을 동기화합니다. 생성 프로젝트는 필요한 AI 자산과 자체 adapter를 사용하며 원래 checkout이나 개인 절대 경로에 의존하지 않습니다.

공유 설정은 공개 템플릿만 포함합니다. token, Cookie, OAuth 키, 비공개 주소, 인증 상태, 로그, 개인 메모리를 커밋하지 않습니다. 자동화 신원과 할당량 모니터링은 대화에 속하며 공유 자산에 넣지 않습니다.

4개 언어 튜토리얼과 현재 승인된 검토 정책은 [Wiki 유지보수 안내](../docs/wiki/site-maintenance.kr.md)를 참조합니다. 사람의 검토는 `HUMAN_REVIEW`를 사용하며, 이 프로젝트 소유자가 명시적으로 승인한 Codex 검토는 `AI_REVIEW`를 사용하고 본문 및 문서별 검토 증거의 해시와 연결합니다. 현재 사용자 허가와 플랫폼 규칙이 작업을 관리하며 Skill은 추가 외부 쓰기 권한을 부여하지 않습니다.
