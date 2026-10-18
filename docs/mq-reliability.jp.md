# メッセージ信頼性（Transactional Outbox）

本書はキュー登録、配信、管理、受信処理の現在の境界を定義します。操作手順と実際の画面は [メッセージ Wiki](wiki/outbox-development.jp.md)、ブレークポイントと競合テストは [ソースデバッグ](wiki/source-debugging.jp.md#outbox) を参照してください。

## 1. 構成

common-core が ReliableMessageRelay を定義し、common-mqlog がローカル Outbox、XXL-JOB 中継、送信戦略を提供します。Base の管理集約は現在 Base と CRM のみを対象とし、全サービスの Outbox を表示するものではありません。

- キュー登録は一意な msgId を生成し、payload を直列化して正の tenantId を検証し、サービス内の sys_mq_message に保存します。broker_type は現在 rocketmq 固定です。
- 4引数 send は REQUIRED を使用します。業務と Outbox の原子的コミットには、同じデータソースと有効なローカルトランザクションが必要です。Controller の @OperLog 後処理は、終了済みの業務トランザクションとの原子性を保証しません。
- 現在のテンプレートは topic に bindingName を保存します。実際の RocketMQ destination は Stream binding が選択し、Base の operlog-out-0 は operlog-topic に接続します。
- 配信は少なくとも1回を目標とします。送信後、SENT 更新前の停止では重複し得るため、受信側に冪等性が必要です。バックオフ、滞留、停止、デッドレター処理が遅延に影響し、一律の「最大10秒」は保証しません。

## 2. ライフサイクル

| 状態 | コード | 許可される動作 |
|---|---|---|
| PENDING | 0 | 期限到達後に取得可能。管理再送は不可 |
| SENT | 1 | 送信戦略が成功。受信側の業務処理の完了とは別 |
| FAILED | 2 | 期限到達後に直接再取得。PENDING への事前更新なし |
| DEAD_LETTER | 3 | 自動再試行停止。修復後の再送またはスキップ |
| SKIPPED | 4 | 管理者が配信停止。再送と再スキップは不可 |
| PROCESSING | 5 | 原子的取得後の120秒リース。期限切れは再取得可能 |

1バッチ最大100件を作成順に取得します。対象は期限到達済み PENDING/FAILED とリース切れ PROCESSING です。取得と結果更新は CAS を使い、取得失敗時に送信せず、更新対象0件で新しい状態を上書きしません。

失敗回数はデータベースで原子的に増加します。既定 max_retry=3 は3回目の失敗でデッドレター化する意味です。1回目は20秒、2回目は40秒、3回目は next_retry_time を消去し、80秒待ちません。不明な broker_type も同じ状態機械を通り、実際の再実行にはスケジューラの巡回も必要です。

- `POST /api/base/mq-message/{msgId}/resend`：FAILED/DEAD_LETTER のみ。CAS で PENDING、retry_count=0 に戻し、バックオフとエラーを消去します。base:mqmessage:resend と非同期配信有効が必要です。
- `POST /api/base/mq-message/{msgId}/skip`：DEAD_LETTER → SKIPPED のみ。base:mqmessage:skip が必要です。
- 状態変更済み・終端操作の繰り返しは HTTP200/業務400、権限不足は HTTP403/業務403。確認画面や送信受付通知は配信完了を意味しません。
- mqRelayHandler は @XxlJob + @SystemJobMeta が必要です。起動準備後に既定 cron `0/10 * * * * ?` で登録・開始し、登録失敗時は10秒ごとに最大12回再試行します。サービスごとに executor AppName を分離します。

## 3. テナント分離

send は明示的な正の Long tenantId を必須とし、ThreadLocal から推測しません。外部管理は信頼済み X-Tenant-Id、認証された内部照会は明示的 tenantId を使います。管理者を含む利用者向け照会、再送、スキップはテナントを限定します。

基盤の中継処理は全テナントを走査します。SRM/調達/資産の TenantLine は sys_mq_message を除外し、他テナントの配信を妨げないことが必要です。

## 4. 制約

- 正式 DDL の唯一の所有者は [common-mq-0001-schema](../database/changelog/common/0001-mq-schema.yaml)。履歴と事前条件で再実行を制御し、実行済み changeSet を編集しません。旧 schema.sql は履歴参照です。
- MessageSender は broker_type で選択します。新戦略にはキュー登録選択契約の拡張も必要で、既存 send で rocketmq 以外は選択できません。
- StreamBridge は業務の重複排除を保証しません。操作ログは一意な eventId を使い、OperLogConsumer は DuplicateKeyException のみ無視し、他の失敗は Broker 再試行のため再送出します。資産受領などは各ドメインの Inbox 契約に従います。
- producer_trace_id はキュー登録時 MDC 由来で null の場合があります。中継は別 Trace を生成し、omniMessageId と取得可能な omniProducerTraceId をヘッダーで渡します。同期的な親子 Span を捏造しません。
- 指標のタグは固定 destination/result/status のみで、テナント、ユーザー、業務キー、payload は含めません。管理ポート、サンプリング、出力は [可観測性](observability.jp.md) を参照してください。
- 非同期配信無効でも Outbox キュー登録は可能です。未配信を消失と判断する前に runtime の deliveryEnabled を確認します。

## 5. ソースと検証

| シンボル | ソース |
|---|---|
| ReliableMessageTemplate.send | [ReliableMessageTemplate.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java) |
| relayPendingMessages / relayOne / handleSendFailure | [MqMessageRelayService.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) |
| claim / CAS | [SysMqMessageMapper.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/mapper/SysMqMessageMapper.java) |
| RocketMqMessageSender.send | [RocketMqMessageSender.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java) |
| MqRelayJobRegistrar | [MqRelayJobRegistrar.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqRelayJobRegistrar.java) |
| resend / skip | [MqMessageAggregationServiceImpl.java](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/MqMessageAggregationServiceImpl.java) |
| eventId | [OperLogConsumer.java](../omni-backend/omni-base/src/main/java/com/omni/base/consumer/OperLogConsumer.java), [OperLogServiceImpl.java](../omni-backend/omni-base/src/main/java/com/omni/base/service/impl/OperLogServiceImpl.java) |
| Trace / Metric | [MqLogMetrics.java](../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/metrics/MqLogMetrics.java) |

[MqMessageRelayServiceTest](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [CAS](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [UnknownBroker](../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java) · [AggregationCas](../omni-backend/omni-base/src/test/java/com/omni/base/service/impl/MqMessageAggregationCasTest.java)

[mq-teaching.flows.spec.ts](../omni-frontend/e2e-docs/flows/mq-teaching.flows.spec.ts) と [制御された fixture](../scripts/fixtures/run-mq-teaching-fixture.ps1) は実際の各段階を検証します。合成 Outbox 行は業務とキュー登録の原子的コミットを証明しません。既定再試行、正式管理操作、Broker からの受信処理の完了、重複排除を別々に確認します。
