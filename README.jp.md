# Omni-Stack

Spring Boot 4 + Vue 3 を基盤とし、テナント分離、権限制御、業務拡張に対応する企業向けマイクロサービスのひな形です。ソースコード、技術的な事実、画像付きチュートリアル、AI 協働のルールを同じリポジトリで管理します。

[简体中文](README.md) · [English](README.en.md) · [한국어](README.kr.md)

## 主な特長

- 公開ホームから Wiki を閲覧できます。ワークスペースには権限のあるアプリを集約し、業務モジュールは新しいタブで開きます。
- マルチテナント、機能権限、データ範囲。CAPTCHA ログイン、ソーシャルログイン、OAuth2 認可に対応します。
<!-- omni:modules:workflow -->
- 独立した Workflow サービスで BPMN のモデリング、公開、承認、合議を扱います。
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB がシステムタスクと個人タスクを管理します。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM はリード、顧客、商談、フォロー活動を扱います。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset が仕入先、調達、資産の業務をつなぎます。
<!-- omni:modules:end -->
- Transactional Outbox、冪等な消費、デッドレター管理。メトリクス、トレース、統合ログ収集は任意で利用できます。
- `tools/omni-cli` がプリセット別の独立プロジェクト、サービス、CRUD を生成します。
- Harness の実践により、簡潔な技術的事実、共有 AI ワークフロー、検証証拠を継続開発に生かします。

技術基盤：Spring Boot 4.0.6、JDK 25、Spring Cloud、Vue 3、TypeScript、Vite、Element Plus。

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[Wiki 全体：紹介、画像付き操作、起動、開発](docs/wiki/index.jp.md)
<!-- omni:preset-table:end -->

[Apache License 2.0](LICENSE) · © Wang Baohai
