# 독립 문서 사이트와 4개 언어 유지보수

저장소 Markdown과 문서 사이트가 대상이며 업무 서비스를 시작할 필요가 없습니다. `docs/docs-manifest.yaml`에 원본을 등록합니다. `docs/.vitepress/source`는 생성물이므로 편집하거나 커밋하지 않습니다.

## 1. 빌드, 시작, 종료

로컬 Docker가 실행되고 4173 포트가 비어 있을 때 저장소 루트에서 실행합니다.

```bash
docker compose -p omni-stack-docs -f compose.docs.yaml up -d --build
docker compose -p omni-stack-docs -f compose.docs.yaml ps
docker compose -p omni-stack-docs -f compose.docs.yaml logs wiki
```

`http://localhost:4173`에서 언어를 선택하고 프로젝트 용어를 검색합니다. 4개 언어 모두 본문과 탐색이 있습니다. 언어 파일이 누락되면 빌드가 실패하며 중국어로 대체하지 않습니다. 종료:

```bash
docker compose -p omni-stack-docs -f compose.docs.yaml down
```

`omni-stack-docs`는 `127.0.0.1:4173`의 Nginx 컨테이너 하나이며 업무 데이터 볼륨은 없습니다. 포트 충돌을 명시적으로 해결합니다. Node로 빌드하고 실행 시에는 정적 파일만 제공합니다.

## 2. 본문 하나, 읽기 진입점 둘

GitHub/Gitee에서 `docs/wiki/index.kr.md`와 상대 링크를 직접 읽습니다. 같은 Markdown으로 `/zh-CN/`, `/en-US/`, `/ja-JP/`, `/ko-KR/` 페이지를 생성하며 생성 디렉터리와 캐시는 제외합니다.

상대 소스 및 규칙 링크는 사이트에서 저장소 링크로 변환됩니다. 기본 플랫폼은 GitHub이며 Gitee는 빌드 시 `DOCS_REPOSITORY_URL=https://gitee.com/wang-baohai/Omni-Stack`으로 설정합니다. 생성 프로젝트는 자체 저장소 URL을 사용합니다. 과거 검수 기록은 해당 커밋을 유지하며 현재 동작을 자동으로 증명하지 않습니다.

`docs/images` 그림을 사이트에 복사합니다. 설명과 코드는 저장소에서도 읽을 수 있습니다. 빌드에 포함된 Mermaid가 브라우저에서 도식 소스를 SVG로 렌더링하고 페이지 또는 밝은·어두운 테마를 전환하면 갱신합니다. 렌더링에 실패하면 소스를 그대로 남깁니다. 큰 도식은 자체 영역 안에서 스크롤할 수 있으며 외부 그림 서비스에 의존하지 않습니다.

## 3. 변경과 품질 검사

1. 중국어 원본과 모든 언어의 본문, 탐색, 그림 설명을 동기화합니다. UI 문자가 있는 그림은 실제 해당 언어에서 촬영합니다.
2. 새 페이지를 등록하고 변경 후 해시를 갱신합니다. 중국어 원본이나 번역문이 변경되면 이전 검토 기록이 무효화됩니다.
3. 내용 완전성, 링크, 스크린샷, 비밀 값, 파생 프리셋 문서, 사이트 빌드를 확인합니다. 내용 검사는 검토 승인과 별개입니다.
4. manifest의 현재 정책에 따라 실제 검토를 마친 후 `reviewer`, `reviewed_at`, `review_type`, `reviewed_source_sha256`, `translation_sha256`를 기록합니다. 사람의 검토는 `HUMAN_REVIEW`를 사용합니다. 소유자는 [ADR-0003](../adr/0003-authorized-ai-translation-review.md)에 따라 Codex의 `AI_REVIEW`를 승인했습니다. 이 방식은 문서별 검토 증거의 경로와 해시도 기록해야 `synchronized`로 설정할 수 있습니다.
5. 검토 대기 목록을 다시 생성합니다. 엄격한 `docs:i18n:check`는 릴리스 조건이며 초안 옵션으로 대체하지 않습니다.

AI 검토는 문서마다 의미와 절차의 완전성, 기술 리터럴, 보안 경계와 실패 조건, 대상 언어, 이미지와 링크를 확인합니다. 사람의 검토로 위장해 기록하지 않습니다. 자동 번역, 플랫폼 작업 승인, 내용 검사만으로는 승인을 기록하지 않습니다. 대기 중인 페이지에는 안내를 표시합니다. 버전, 날짜, 증거는 실제 실행 사실을 반영해야 합니다.

업무 화면 증거는 로컬 Docker 안의 Playwright로 수집합니다. `E2E_RUNTIME_MODE=external`을 선택하고 확인한 Docker 프런트엔드에 연결합니다. `E2E_BASE_URL`, `E2E_TENANT_ID`, 고유한 `E2E_EXECUTION`, `E2E_REGISTRY_PATH`와 저장소 밖의 `E2E_ARTIFACT_DIR`을 명시합니다. 검토 전 그림은 `E2E_DOCS_IMAGE_DIR`로 저장소 밖의 경로를 지정합니다. 시각 검토, 마스킹, 출처 등록을 마친 뒤 필요한 그림만 공식 자료에 추가합니다. 실제 로그인 CAPTCHA는 사람이 입력하고 테스트용 단기 토큰은 테스트 classpath의 관리된 fixture로 발급합니다.

`E2E_RUNTIME_MODE=display-fixtures`는 화면 시연 전용입니다. CAPTCHA, 테넌트, 기기 코드 응답을 대체하고 시계를 고정합니다. 결과에 모의 화면임을 표시하며 실제 인증이나 업무 흐름의 성공 증거로 등록하지 않습니다. 공식 `external` 모드는 실제 API와 시간을 사용하며 남은 시간도 그대로 표시합니다. 의도적인 장애 주입은 시험 범위를 별도로 명시합니다. `npm run docs:screenshots:test`는 두 모드의 경계를 검증하며 이 명령도 Docker 프런트엔드 도구 컨테이너에서 실행합니다.

## 4. 정적 결과물

`docker/docs/Dockerfile` 빌드 단계에서 `/workspace/docs/.vitepress/dist`를 생성합니다. 실행 이미지는 정적 결과물만 포함합니다. 빌드한 컨테이너에서 추출하여 다른 웹 서버에 배포할 수 있습니다. 하위 경로 배포는 빌드 시 `DOCS_BASE`를 지정하고 탐색, 검색, 그림을 검증합니다.

업무 이미지는 루트 `.dockerignore`로 docs와 ai를 제외하고 문서 이미지는 전용 제외 규칙을 사용합니다. 릴리스 전에 엄격한 언어·그림 검사를 완료합니다. 로컬 미리보기는 GitHub Pages, Gitee, 운영 서버에 자동 게시하지 않습니다.

## 5. 문제 해결

| 현상 | 확인 |
|---|---|
| 페이지 또는 언어 누락 | 매니페스트 원본·번역 경로와 신규 등록 |
| 사이트 링크 오류 | 원본 상대 경로와 다시 생성한 결과물 |
| 검색 결과 없음 | 현재 언어, 매니페스트·검색 색인 포함 여부 |
| 그림 누락 | 원본 경로, 빌드 컨텍스트, 그림 매니페스트, 언어 |
| 검토 실패 | 검토자, 날짜, 원문·번역문 해시. AI 검토는 승인과 증거 해시도 필요하며 변경 후 실제 재검토 |
| 오래된 내용 | 재빌드 후 원본 해시와 이미지 ID 비교 |
