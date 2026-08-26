# Omni-Stack スキャフォールドアップグレード実施計画

> 翻訳状態：中国語原文の技術翻訳ドラフトであり、人手レビューは未完了です。
> 実施上の事実源は `docs/scaffold-upgrade-implementation-plan.md` です。

## 0. 正確性の境界

本計画は実行可能、証拠ベース、rollback 可能ですが、将来の実装を事前に「100% 正しい」と保証するものではありません。事実は repository baseline に結び付け、選択は明示的 decision とし、各 work package に検収、互換、証拠、復旧条件を設定します。

## 1. 目的、範囲、非目標

前提となる DB 基盤と R-01～R-10 を実装します。承認ルール UX、共通 Starter、service/CRUD generator、preset、lightweight mode、observability、frontend quality、4 言語 delivery、screenshots、最終 cleanup が対象です。無関係な業務 domain の追加、technology stack 置換、本番 Secret 作成、CAPTCHA／認証 bypass は対象外です。

## 2. 基準と原則

各 phase の前に branch、commit、dirty files、tool version、module/file count、Compose services、DB state、build、lint、E2E を記録します。利用者の変更を保護し、中国語の architecture/domain docs を事実源とします。forward-only DB、fail-closed tenant/security、単一 metadata catalog、決定論的生成、隔離 fixture、独立証拠を原則とします。

## 3. 決定 D-01～D-09

| Decision | 既定方針 |
|---|---|
| D-01 | Liquibase と `omni-db-migrator` が schema version を管理し、既存 DB は fingerprint と backup 後だけ adopt する。 |
| D-02 | 承認 preview と実申請は同じ server resolver／result model を使う。 |
| D-03 | 共通 infrastructure は `omni-common-service`、業務 semantics は各 domain service に置く。 |
| D-04 | version 管理された module catalog を generator／preset の唯一の事実源とする。 |
| D-05 | generator は plan／dry-run を先に行い、競合を既定で拒否し、atomic write する。 |
| D-06 | preset は dependency-closed な出力であり runtime feature flag ではない。 |
| D-07 | lite/full は同一 application code を使い、宣言的 infrastructure／config だけ変える。 |
| D-08 | OTel 互換 trace、Prometheus metrics、structured logs、dashboard、alert、SLO を観測基準とする。 |
| D-09 | 中国語を原文とし、manifest と人手レビューなしに synchronized としない。 |

変更する場合は依存作業の前に ADR を作成します。

## 4. 目標構成と順序

DB migrator、common service starter、scaffold CLI/catalog/templates、preset 定義／保守文書、observability profile、docs manifest、screenshot suite、evidence、cleanup report を整備します。DB versioning は generator／cleanup より先、Starter 抽出は template より先、安定した UI／fixture は最終画像より先です。

## 5. WP-00：DB version 管理

全 SQL、table、seed、vendor schema、procedure、tenant provisioning を棚卸しし、順序化した Liquibase changelog と `omni-db-migrator` を作ります。read-only preflight、既存 DB fingerprint/adopt、backup 必須、checksum、lock、failure recovery を実装し、必要な一時 procedure は tested Java orchestration に置換します。

空 DB fresh、匿名化既存 DB upgrade、再実行、途中失敗復旧、新 tenant provisioning を検証します。rollback は backup/restore と application compatibility を使用し、その場で推測した破壊的 reverse SQL は使いません。

## 6. WP-01：業務向け承認ルール UI

業務名と互換 migration、公開済み workflow options、安全な match／coverage／impact preview、理解しやすい component を実装し、既存 permission code を維持します。preview と実申請は同じ resolver を使い、境界、fallback、overlap/gap、未公開 flow、audit、Trace ID、3 viewport を検証します。

## 7. WP-08：frontend lint／型治理

unsafe `any`、console、reactivity、formatting の順で解消し、BPMN と複雑 API に narrow adapter／type guard を置きます。lint error 0／warning 0、production build、critical browser E2E を CI gate にし、rule 弱体化で warning を隠しません。

## 8. WP-02：共通 Service Starter

重複／責任 matrix を先に作り、tenant、gateway pre-auth、DataScope wiring、internal API auth、XSS、audit 等の infrastructure だけ抽出します。interceptor order と fail-closed を保持し、CRM pilot 後に SRM、Procurement、Asset、Base 適用部へ展開します。Auth/Gateway 特例は明記します。

auto-config condition/opt-out、tenant isolation、DataScope、XSS、internal auth、permission、4 service regression を検証し、採用確認までは旧実装へ戻せる状態を保ちます。

## 9. WP-03：`create-service` CLI

catalog validation、deterministic plan、dry-run、conflict report、上書き拒否、atomic write を実装します。Maven module/layer、config、test、Docker/Compose、Gateway/Nacos、frontend/API/i18n/menu/permission、XSS SPI、docs、lock metadata を生成します。

golden service が build、start、health/security smoke、drift なし再生成、記録された plan による removal を通過することが検収条件です。

## 10. WP-04：Full-stack CRUD generator

安全な type mapping、constraint、ownership/DataScope、permission、UI field を持つ descriptor を定義します。forward-only Liquibase、backend layers/tests、frontend API/route/page/i18n、permission/menu/seed assertions、E2E skeleton を生成します。CRUD、validation、pagination、authorization、tenant、ownership、XSS、regeneration、clean build を golden test で確認します。

## 11. WP-05：Project preset

`core`、`workflow`、`crm`、`supply-chain`、`full` の依存閉包と不正組合せ検証を定義し、report 付きで target directory に生成します。source monorepo は暗黙変更しません。生成 dependency matrix と 4 言語 user/maintainer guide を維持し、各 preset の fresh generate、build、DB init、start、login、core E2E を検証します。

## 12. WP-06：Lightweight mode

Compose profile/local config と module-focused commands を追加します。optional infrastructure 不在は明確な degradation message を返します。起動時間／resource と full behavior を比較し、business code、security、schema、contract が同一であることを検証します。

## 13. WP-07：Observability

gateway、HTTP/Feign、workflow、job、MQ の Trace ID/MDC を統一し、OTel export、Prometheus、cardinality を抑えた metric、structured log、local Grafana/Tempo/Loki/Alloy、dashboard、alert、SLO を提供します。同期／非同期 trace、log correlation、metrics、alert、profile off、sensitive filtering、overhead を検証します。

## 14. WP-09：4 言語文書と画像

`docs-manifest` に source/translation hash と review state を記録します。ja-JP／ko-KR UI と多値 language selector を完成させ、中国語 fact docs を更新後に翻訳・人手レビューします。4 README の意味を揃えます。

隔離 Playwright docs suite と非本番 deterministic fixture を使用します。manifest に stable ID、language、role、route、viewport、前提、mask、期待結果を記録し、public/auth、system、Workflow、Scheduling、CRM、SRM、Procurement、Asset、MQ/monitoring、permission、failure、desktop、重要 mobile flows を対象とします。password、CAPTCHA 答、token、個人／本番データを保存しません。CI は link、translation drift、image reference/orphan、sensitive content、critical browser execution を検査し、初心者 walkthrough を証拠化します。

machine translation は `present-unverified` のままにし、氏名／日付を伴う人手 review 後だけ synchronized にします。

## 15. WP-10：最終 Cleanup

先行 gate 完了後に temporary name／extension、untracked、SQL、BPMN、images、reference を棚卸しし、keep／promote／replace／delete に分類します。再利用処理を先に正式化し、category ごとの commit で削除、dangling reference scan、Git tag/history による回復を行います。

最終 repository は production source、formal tests、build/deploy config、final docs/images、templates、明確な入口と owner を持つ stable automation のみを保持します。人手 SQL は `scripts/sql/seed` の最終冪等 seed のみです。release 前に fresh/upgrade、全 preset、full build/E2E、security regression、`cleanup-report` を完了します。

## 16. Global gate と release

G0 baseline、G1 DB、G2 approval/frontend、G3 starter、G4 generators、G5 presets/lite、G6 observability、G7 docs/screenshots、G8 cleanup/release とします。失敗 gate は依存作業を停止します。schema/API 追加、互換 producer/consumer、切替、観測、旧 code 削除の小さな段階で release し、backup、restore、application rollback、data compatibility を記録します。

## 17. 工数／Token

総計 131～199 人日、7.05M～11.65M token、運用目標約 9.5M、12M 到達前に再承認とします。WP-09 は 25～40 日、1.50M～2.60M token の最大文書項目です。読解、生成、tool output、retry、test、分析、文書を含む計画値であり価格保証ではありません。

## 18. Risk control

P0 は既存 DB 誤 adopt、tenant init 消失、security/interceptor 変化、preview/submission 差異、generator overwrite、不完全 preset graph、cleanup 誤削除です。fingerprint/backup、contract test、fail-closed regression、shared evaluation、dry-run/atomic write、dependency closure、inventory、独立 review、recovery exercise で制御します。translation drift、screenshot leak/flaky、telemetry overhead、lite/full divergence、external version drift、budget overrun は owner と証拠を持つ P1 とします。

## 19. 実行順序

S0 は baseline と WP-00、S1 は WP-01/WP-08、S2 は WP-02、S3 は catalog/WP-03/WP-04、S4 は WP-05/WP-06、S5 は WP-07、S6 は human review と初心者 walkthrough を含む WP-09、S7 は inventory、cleanup、最終 acceptance、release evidence です。ticket には dependency、scope、test、rollback、evidence を記載します。

## 20. 証拠と計画保守

`docs/evidence/scaffold-upgrade` に commit、environment、commands、exit code、pass/fail/skip、匿名 DB provenance、Compose health、E2E index、performance、limitations、owner、follow-up を保存します。大容量 log/trace は CI artifact に置きます。code fact 変更時は fact docs と本計画を更新し、package 20%／全体 10% 超の見積変化を記録します。

## 21. Ready／Done

D-01～D-09 の承認または ADR、reviewer/owner、隔離 infrastructure/browser、匿名 upgrade DB、CAPTCHA bypass を伴わない短期 test authentication、利用者変更の保護が Ready 条件です。

WP-00 と R-01～R-10、backend/frontend、CI browser、5 presets、fresh/upgrade/recovery、generators、lite/full、observability、4 言語 UI/docs/images の人手 review、beginner walkthrough、cleanup、security、backup/rollback drill、実績工数/token/risk 記録がすべて完了した時だけ Done です。
