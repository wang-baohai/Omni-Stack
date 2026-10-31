# 定期実行の制約

本書は定期実行の事実と工程上の制約を定義します。基礎概念、フレームワーク原文、工程実装、実画面は [XXL-JOB の独立章](wiki/distributed-jobs-principles.jp.md)、既存の詳細操作は [定期実行チュートリアル](guides/scheduling.jp.md)、実行設定は [Docker デプロイ](docker-deployment.jp.md) を参照してください。

## 1. 境界とデータの所有者

| 所有者 | 責務 |
|---|---|
| `omni-common-core` | Spring に依存しない `UserJobHandler` SPI と `UserJobMessage` |
| `omni-common-job` | XXL-JOB 実行器、Admin クライアント、システムメタデータと自動登録 |
| `omni-base` | システム/種別管理、個人ジョブの所有権検証、ハンドラーの振り分けと実行ログ |
| XXL-JOB Admin | Cron、実行器検出、起動/コールバック記録。個人ジョブの操作権限は管理しない |

`sys_user_job_type` は共通カタログで、`type_code` は一意、テナント列はありません。`sys_user_job` はテナントと作成者、`sys_user_job_log` はメッセージのテナントと `jobId` を保存します。ワークベンチは親のテナント／作成者を検証してから `jobId` でログを検索します。現在のログサービスは `tenantId` 引数を SQL 条件に追加しません。`xxl_job_id` がスケジューラー行を関連付けます。名前を信頼できる関連キーとして使わないでください。

## 2. システムジョブ

- メソッドには `@XxlJob("handlerName")` と `@SystemJobMeta(...)` の両方が必要です。一方がないと `SystemJobRegistry` に登録されません。
- `SystemJobRegistry.scan()` がメタデータを収集します。実行器が有効な場合、`XxlJobAutoConfiguration.systemJobAutoRegistrar()` は未登録ジョブを登録し、既存の Cron/パラメータを維持します。
- サービスは `omni-common-job` を必要とします。自動構成には classpath 上の `XxlJobSpringExecutor` が必要です。実行器 Bean と登録 Runner は `xxl.job.executor.enabled=false` の場合に無効になります。
- サービスごとに AppName を分けます。同名 handler は別の実行器グループに共存できます。登録、検索、削除には正しいグループを指定します。
- `/api/job/system-job/list` は `job:system-job:list`、登録/開始/停止/起動/削除は `job:system-job:manage` が必要です。一覧の状態は `UNREGISTERED`、`RUNNING`、`STOPPED` です。リモート一覧の検索失敗時も `UNREGISTERED` に戻る場合があり、この表示だけでリモート不存在を証明できません。
- システム結果は個人ログではなく XXL-JOB ログで確認します。起動要求の成功はスケジューラー側の受理のみを意味します。

## 3. 個人ジョブの契約と整合性

- `/api/base/my-job` の作成は信頼できるヘッダーと認証文脈からテナントと作成者を取得します。更新/削除/状態変更/起動/ログは `MyJobController.verifyOwnership(id, tenantId, username)` で両方を照合します。行単位の所有権検証を `@PreAuthorize` で置き換えてはいけません。
- 一覧/統計は現在のテナントと `createBy` に固定されます。有効な種別一覧は共通カタログです。`/api/job/user-job-type` の管理には `job:user-job-type:*` 権限を使います。
- `UserJobMessage` のフィールドは `jobId`、`tenantId`、`jobType`、`jobName`、`jobParams` です。すべての個人ジョブは `userJobExecuteHandler` を登録し、内部で `jobType` により Bean を検索します。
- 作成はローカル行を挿入し、XXL-JOB を登録して ID を書き戻します。正常な作成直後は有効です。ローカル `status=1` は有効、`0` は一時停止です。
- 登録/ID 書き戻しの失敗時は実行器グループ、handler、テナント/ローカル ID を含む `executorParam` で対象を厳密に特定します。リモートに残っていないことを確認してからローカル行を削除します。結果が不明なら復旧用の関連を残してエラーとし、ロールバック完了を宣言したり、確認なしに再作成したりしてはいけません。
- XXL ID が関連付けられている場合、Cron/パラメータ更新、状態変更、削除はリモート操作を先に行います。失敗時はローカル旧値/関連を残します。削除失敗時は完全なページ走査でリモート不存在を確認します。名称だけの変更は XXL-JOB の説明を更新しません。
- 一時停止は今後の Cron 起動を止めますが、手動起動は禁止しません。受理と実行完了は別段階です。新しいログ ID、時刻、XXL-JOB の起動元を確認します。
- 実行ログの `status=1` は成功、`0` は失敗です。ハンドラー欠落や捕捉したハンドラー例外は失敗ログの保存を試行し、JSON デシリアライズ失敗はログ保存前に戻ります。ログ挿入と最終時刻更新に共通の宣言的トランザクションはなく、DB 例外で最終結果設定が中断されるため、すべての失敗に個人ログがあるとは保証しません。
- `XxlJobAdminClient` のセッション Cookie はメモリ内だけに保持します。応答 code が 200 以外、または `not login` を含む場合は再ログインして一度再試行しますが、業務書き込みの冪等性を保証するものではありません。Cookie をファイルや公開証拠に保存しません。

## 4. 拡張の制約

1. `UserJobHandler` を実装し、`@Component` の Bean 名をカタログの `type_code` と完全一致させます。有効なカタログ行だけではハンドラーのデプロイを証明できません。
2. パラメータを検証し、HTTP スレッドの文脈に依存せずメッセージの `tenantId` を明示的に使います。業務作用は冪等にし、Cron と手動実行の重複を想定します。
3. `param_template` は標準 JSON Schema `object/properties` と互換の平坦形式を扱います。列挙値は安定値を送信し、表示ラベルを振り分けコードにしません。
4. シード変更は `scripts/sql/seed/base.sql` と `database/seed/manifest.yaml` の該当チェックサム/検証を更新します。スキーマは前向き移行を使います。
5. 新しいシステムジョブは両アノテーションで既定 Cron、パラメータ、戦略を定義します。個人ジョブ共通 handler をシステムジョブとして分類しません。
6. ローカル Docker で作成、編集、一時停止、手動起動、再開、実 Cron、成功/失敗ログ、所有権拒否、削除後のリモート残存ゼロを検証します。

## 5. ソース入口

- [個人コントローラ](../omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java)：一覧の絞り込みと `verifyOwnership()`。
- [個人サービス](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobServiceImpl.java)：リモート先行、厳密な補償、不明結果の保持。
- [Admin クライアント](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/XxlJobAdminClient.java)：登録、全フィールド更新、完全ページ走査、重複ページ防止。
- [共通実行入口](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java)、[振り分けレジストリ](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java)、[水分補給ハンドラー](../omni-backend/omni-base/src/main/java/com/omni/base/job/handler/DrinkWaterRemindHandler.java)。
- [自動構成](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/config/XxlJobAutoConfiguration.java)、[システムレジストリ](../omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java)、[シード同期](../omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobSeedSyncRunner.java)。シード同期は有効かつ XXL ID がない行を走査します。再起動を不明結果の照合の代わりにしてはいけません。

- [個人ログ検索](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/UserJobLogServiceImpl.java)：現在は入口の親所有権検証と固定 jobId に依存します。
- [システムジョブサービス](../omni-backend/omni-base/src/main/java/com/omni/base/service/SystemJobService.java)：メタデータとリモート状態の統合、および検索失敗時の表示を確認できます。

## 6. 実行と検証の境界

基盤は `compose.infra.yaml` と migrator が管理します。XXL-JOB のスキーマ/シードは `database/changelog/xxl-job/`、`scripts/sql/seed/xxl-job.sql` です。資格情報と access token は非公開 `.env` から取得し、教材の固定値を使いません。サービス間接続は `xxl-job-admin:8080/xxl-job-admin`、ホストの公開ポートは実際の Compose で確認します。

日時は `yyyy-MM-dd HH:mm:ss` を使います。アプリ、ビルド、テスト、画像、証拠はローカル Docker で実行します。教材の証拠には操作者、イメージ、ソース、実際の起動元、制限を明記します。カタログ画像、受理応答、模擬障害だけでは実行の完了を証明できません。
