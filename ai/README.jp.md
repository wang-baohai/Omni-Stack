# 共有 AI 工程資産

`ai/` が正規ソースです。ルート [AGENTS](../AGENTS.md) と[技術的事実](../docs/architecture.jp.md)が制約と現在の動作を定義します。[一覧](manifest.yaml)に出所、版、許諾、依存、対象ツールを記録します。

## 内容

- `skills/`：既存 grill-me とプロジェクト変更手順 omni-change。
- `prompts/`：変更レビューと文書同期のテンプレート。
- `knowledge/`：出所、日付、失効条件を持つ経験。第二の契約ではありません。
- `adapters/`：Codex/Qoder の検出入口を生成し、ユーザー変更の上書きを拒否します。

自作プラグインのソースは見つかりませんでした。外部ツールは公式出所だけを登録し、インストール済みキャッシュをコピーしません。2026-10-12、Docker 内の読み取り専用 Git 検証で grill-me のファイル全体 SHA-256 が[固定上流コミット](https://github.com/mattpocock/skills/blob/694fa30311e02c2639942308513555e61ee84a6f/skills/productivity/grill-me/SKILL.md)と一致することを確認し、その [MIT ライセンス](skills/grill-me/LICENSE)を保存しました。過去の導入過程は独立して証明していません。個人のグローバルプラグイン、アカウント、記憶は含みません。

## インストールと確認

リポジトリ直下でコンテナツールを構築し、CLI 依存を入れます。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --apply
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=codex --check
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli node ../../ai/adapters/bootstrap.mjs --tool=qoder --check
```

プレビューは書き込みません。`--apply` は `.agents/skills/<name>/SKILL.md` または `.qoder/skills/<name>/SKILL.md` を派生し、ローカル変更を保護します。正規ソースの変更後に再インストールし、派生ファイルは直接変更しません。検出にはワークスペース再読込や新しいターンが必要な場合があります。ディレクトリ検証だけでは実際のロードを証明できません。

[2026-10-18 のネイティブ検出検証](../docs/reference/evidence/qoder-native-skill-discovery-2026-10-18.json)では、分離した Docker コンテナー内で公式 Qoder CLI（npm 互換配布版）`1.1.73` を使用し、生成後に移動した5種類のプリセットで `qodercli skills list` を実行しました。`grill-me` と `omni-change` はどちらも `Enabled` で、名前と説明は正規ソースに一致し、場所は各プロジェクトの派生エントリーを指していました。プリセットごとに個別の新規設定を使い、ネットワークを無効にしました。アカウントやグローバル状態はコピーせず、モデル要求も送信していません。検証対象は CLI のプロジェクト Skill 検出であり、IDE の画面や Skill タスク実行は含みません。

## 保守とプリセット

正規ソースだけを変更し、一覧の版と派生入口を同期します。生成プロジェクトは必要な AI 資産を持ち、自身の adapter で導入します。元の checkout や個人の絶対パスに依存しません。

共有設定は公開テンプレートだけです。token、Cookie、OAuth 鍵、秘密アドレス、認証状態、ログ、個人記憶をコミットしません。自動化識別情報と利用枠監視は会話に属し、共有資産には入れません。

四言語教材と現在承認されたレビュー方針は [Wiki 保守ガイド](../docs/wiki/site-maintenance.jp.md)を参照します。人によるレビューは `HUMAN_REVIEW`、本プロジェクトの所有者が明示的に承認した Codex のレビューは `AI_REVIEW` を使い、本文と文書ごとのレビュー証拠にハッシュで結び付けます。操作は現在のユーザー許可とプラットフォーム規則に従い、Skill が追加の外部書込権限を与えることはありません。
