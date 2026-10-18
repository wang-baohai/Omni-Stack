# Omni-Stack

Spring Boot 4 + Vue 3 を使用した、テナント、権限、承認、業務ドメインを必要とする企業向けマイクロサービス基盤です。ソース、技術的制約、操作教材、再利用可能な AI ワークフローを一緒に提供します。

[简体中文](README.md) · [English](README.en.md) · [한국어](README.kr.md)

## 主な機能

- テナント分離、機能権限、データ範囲、CAPTCHA、ソーシャルログイン、OAuth2 認可。
<!-- omni:modules:workflow -->
- 独立した Workflow による BPMN 設計、公開、承認、合議。
<!-- omni:modules:end -->
<!-- omni:modules:xxl-job -->
- XXL-JOB による個人・システムタスク。
<!-- omni:modules:end -->
<!-- omni:modules:crm -->
- CRM の営業フロー。
<!-- omni:modules:end -->
<!-- omni:modules:srm,procurement,asset -->
- SRM → Procurement → Asset の仕入先・購買・資産連携。
<!-- omni:modules:end -->
- トランザクション Outbox、冪等消費、デッドレター、任意の指標・追跡・ログ基盤。
- `tools/omni-cli` によるサービス、CRUD、プリセットに応じた独立プロジェクト生成。
- [AGENTS.md](AGENTS.md) と技術的事実による実行制約、[AI 資産](ai/README.jp.md)による再利用可能なツール入口。

主な技術：Spring Boot 4.0.6、JDK 25、Spring Cloud、Vue 3、TypeScript、Vite、Element Plus。正確な依存バージョンは POM と npm ロックファイルを参照します。

## 最短の体験

[初回起動ガイド](docs/guides/quick-start.jp.md)に従い、コードとローカル Docker を準備し、`.env` の必須秘密情報をすべて設定します。JDK と Node はビルドコンテナが提供します。

```bash
docker compose --profile full up -d --build
docker compose ps
```

画面：`http://localhost:3000`。開発テナントとシードアカウントを選択し、現在の CAPTCHA を手入力します。一部の業務のみを使う場合は先にプリセットを選びます。構造移行と基本シードは migrator が管理します。[データの説明](docs/wiki/data-and-fixtures.jp.md)を参照してください。

<!-- omni:preset-table:start -->
`core` · `crm` · `full` · `supply-chain` · `workflow`

[選択ガイド](docs/preset-quick-selection.jp.md) · [プリセット依存関係マトリクス](docs/preset-dependency-matrix.jp.md)
<!-- omni:preset-table:end -->

## 文書と貢献

- [プロジェクト Wiki](docs/wiki/index.jp.md)：コード取得、業務操作、デバッグ、拡張、運用。
- [構成と技術的事実](docs/architecture.jp.md)、[API 契約](docs/api-contract.jp.md)：保守者向けの現在の動作基準。
- [独立文書サイト](docs/wiki/site-maintenance.jp.md)：Docker、ローカル検索、静的公開。
- [リポジトリ規則](docs/repository-governance.md)、[変更履歴](CHANGELOG.md)、[構成の決定](docs/adr/0001-scaffold-upgrade-foundations.md)。

貢献前に関連規約を読み、影響する検証を Docker で実行します。Issue と PR は [GitHub](https://github.com/wang-baohai/Omni-Stack) または [Gitee](https://gitee.com/wang-baohai/Omni-Stack) へ送れます。

[Apache License 2.0](LICENSE) · © Wang Baohai
