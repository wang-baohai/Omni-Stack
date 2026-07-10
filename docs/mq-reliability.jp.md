# 信頼性の高いメッセージ送信（Transactional Outbox）

> 本文書は Omni-Stack の Transactional Outbox パターン実装について説明し、メッセージの最低1回配信を保証します。  
> アーキテクチャの概要は [architecture.jp.md](architecture.jp.md) を参照してください。Docker デプロイ設定は [docker-deployment.jp.md](docker-deployment.jp.md) を参照してください。

Omni-Stack は **Transactional Outbox** パターン + **XXL-JOB** リレースケジューリングにより、メッセージの最低1回配信を保証します。システムは MQ に直接メッセージを送信するのではなく（トランザクションロールバック時のデータ損失を避けるため）、PENDING レコードをローカルの `sys_mq_message` テーブルに書き込み（同じデータベーストランザクション内）、バックグラウンドリレータスクが非同期で MQ Broker に配信します。

## 1. Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────────┐
│                        Business Service (e.g., omni-auth)                 │
│                                                                            │
│  @OperLog method ──> OperLogAspect ──> OperLogProducer                     │
│                                            │                               │
│                                            ▼                               │
│                                    ReliableMessageRelay (interface)         │
│                                            │                               │
│  ┌─────────────────────────────────────────┼─────────────────────────────┐ │
│  │            omni-common-mqlog            │                             │ │
│  │                                         ▼                             │ │
│  │                              ReliableMessageTemplate                   │ │
│  │                                  │                                     │ │
│  │                    INSERT sys_mq_message (PENDING)                      │ │
│  │                    [same local transaction]                             │ │
│  └─────────────────────────────────────────┼─────────────────────────────┘ │
└────────────────────────────────────────────┼───────────────────────────────┘
                                             │
┌────────────────────────────────────────────┼───────────────────────────────┐
│  XXL-JOB Scheduler (every 10s)            ▼                               │
│                                                                            │
│  MqMessageRelayJob (@XxlJob + @SystemJobMeta)                              │
│       │                                                                    │
│       ▼                                                                    │
│  MqMessageRelayService                                                     │
│       │ fetchPendingMessages()  ──> PENDING / FAILED & backoff expired     │
│       │                                                                    │
│       ▼ relayOne(msg)                                                      │
│  MessageSender strategy ──> RocketMqMessageSender (StreamBridge)          │
│       │                                                                    │
│       ├── Success  ──> status = SENT                                       │
│       └── Failure  ──> retry_count++, exponential backoff                  │
│                         └── exceeds max_retry ──> DEAD_LETTER              │
└────────────────────────────────────────────────────────────────────────────┘
```

**モジュール依存関係**:

| モジュール | 役割 |
|--------|------|
| `omni-common-core` | `ReliableMessageRelay` インターフェースを定義（純粋な POJO、Spring 依存なし） |
| `omni-common-mqlog` | Outbox パターンを実装：`ReliableMessageTemplate`、`MqMessageRelayService`、`MqMessageRelayJob`、`MessageSender`、自動設定 |
| `omni-common-operlog` | オプション呼び出し元：`OperLogProducer` は利用可能な場合に `ReliableMessageRelay` を使用し、それ以外は直接 `StreamBridge` にフォールバック |
| `omni-base` | フロントエンド管理 UI 用の外部管理コントローラ `MqMessageController` |

**主要な設計判断**:

- **なぜ直接送信ではなく Outbox か？** トランザクション内での直接 MQ 送信は分散トランザクションの問題を引き起こします — MQ 送信が成功しても DB トランザクションがロールバックされると、コンシューマは幽霊メッセージを受信します。Outbox パターンはすべてを単一のローカルトランザクションに収めます。
- **なぜリレーエンジンに XXL-JOB を使うのか？** プロジェクトはすでにスケジューリングに XXL-JOB を使用しています。メッセージリレーに再利用することで、別のポーリングデーモンを導入する必要がなくなります。各サービスのエグゼキュータ AppName は異なるため、同じ `mqRelayHandler` 名はサービス間で自然に分離されます。
- **なぜ明示的な tenantId パラメータか？** テナント分離は API レベルで保証される必要があります — 暗黙的な ThreadLocal 解決は脆く、エラーが発生しやすいです。すべての呼び出し元は `tenantId` を明示的に渡す必要があります。

## 2. Message Lifecycle

### 2.1 Status Machine

```
                         ┌──────────┐
                INSERT   │          │   relay success
  ─────────────────────> │ PENDING  │ ──────────────────> SENT
                         │  (0)     │                      (1)
                         │          │
                         └────┬─────┘
                              │ relay failure
                              ▼
                         ┌──────────┐
                         │          │   retry < max_retry
                         │ FAILED   │ ──────────────────> back to PENDING
                         │  (2)     │   (with next_retry_time = now + 2^count × 10s)
                         │          │
                         └────┬─────┘
                              │ retry >= max_retry
                              ▼
                         ┌──────────┐
                         │DEAD_LETTER│
                         │  (3)     │
                         └────┬─────┘
                              │
                    ┌─────────┴─────────┐
                    │ resend             │ skip
                    ▼                    ▼
               PENDING (0)         SKIPPED (4)
```

| ステータス | コード | 説明 |
|--------|------|-------------|
| PENDING | 0 | 配信待ちまたはリトライ準備完了 |
| SENT | 1 | MQ への配信成功（終端状態） |
| FAILED | 2 | 配信失敗、次回のリトライ待ち（バックオフ） |
| DEAD_LETTER | 3 | 最大リトライ回数超過（終端状態、手動対応が必要） |
| SKIPPED | 4 | 管理者により手動で無視としてマーク（終端状態） |

### 2.2 Write Path

`ReliableMessageTemplate` は `ReliableMessageRelay` インターフェースを実装します：

```java
// OperLogProducer または任意のビジネスコードから呼び出される
reliableMessageRelay.send("oper-log-out-0", operLogMessage, tenantId);
reliableMessageRelay.send("order-out-0", orderPayload, tenantId, "order:12345");
```

内部処理：
1. UUID を `msgId`（重複排除キー）として生成
2. `ObjectMapper` 経由でペイロードを JSON にシリアライズ
3. `SysMqMessage` レコードを構築：`status = PENDING`、`brokerType = "rocketmq"`、`tenantId`、`serviceName`
4. MyBatis-Plus 経由で `sys_mq_message` に INSERT（`@Transactional(REQUIRED)` 内）

### 2.3 Relay Path

`MqMessageRelayJob` は XXL-JOB によりスケジューリングされます（デフォルト：10秒ごと、FIRST ルート戦略）：

1. `fetchPendingMessages()` — `status IN (PENDING, FAILED)` かつ (`next_retry_time IS NULL` または `next_retry_time <= NOW()`) のレコードを SELECT、`create_time ASC` でソート、LIMIT 100
2. 各メッセージについて、`broker_type` で sender マップから `MessageSender` を検索
3. `sender.send(msg)` を呼び出し — 成功時は SENT にマーク、失敗時はリトライカウントを増加し指数バックオフを適用
4. `retryCount >= maxRetry` の場合、DEAD_LETTER にマーク

### 2.4 Retry Strategy

指数バックオフの計算式：**2^retryCount × 10 秒**

| リトライ | バックオフ | 次回リトライまでの時間 |
|-------|---------|-----------------|
| 1 | 2^1 × 10 = 20s | 約20秒 |
| 2 | 2^2 × 10 = 40s | 約40秒 |
| 3 | 2^3 × 10 = 80s | 約80秒 |

デフォルト `max_retry = 3`。3回失敗後、メッセージは DEAD_LETTER ステータスになります。

### 2.5 Dead Letter Handling

デッドレターはフロントエンド管理 UI（`運用監視 → メッセージ記録`）を通じて管理者の手動介入が必要です：

- **再送信** (`POST /api/base/mq-message/{msgId}/resend`)：ステータスを PENDING にリセットし、リトライカウントとバックオフタイマーをクリアします。リレータスクが次回のポーリングでピックアップします。
- **スキップ** (`POST /api/base/mq-message/{msgId}/skip`)：DEAD_LETTER → SKIPPED に遷移し、メッセージが配信されないことを確認します。

## 3. Tenant Isolation

### 3.1 Design: Explicit Parameter (No ThreadLocal)

`ReliableMessageRelay.send()` メソッドは明示的な `Long tenantId` パラメータを必要とします。これは意図的な設計判断です：

- **ThreadLocal の魔法なし**：ThreadLocal ベースのテナント解決は脆い — 非同期境界、スレッドプール引き継ぎ、スケジュールタスクで失われる可能性があります。
- **コンパイル時の安全性**：パラメータが欠けているとコンパイルエラーが発生し、実行時のサイレントバグにはなりません。
- **呼び出し元の責任**：各呼び出し元は自身のコンテキストから tenantId を抽出します（例：`OperLogMessage.getTenantId()`、`@RequestHeader("X-Tenant-Id")`）。

```java
// 正しい：明示的な tenantId
reliableMessageRelay.send("oper-log-out-0", message, message.getTenantId());

// 間違い：tenantId を省略するとコンパイルエラーになる
reliableMessageRelay.send("oper-log-out-0", message);
```

### 3.2 Write: tenantId in Outbox Record

`ReliableMessageTemplate.send()` は `sys_mq_message` に INSERT する前に `message.setTenantId(tenantId)` を設定します。`tenant_id` カラムには効率的なテナントスコープクエリ用のインデックス（`idx_tenant_time`）があります。

### 3.3 Read: All Query Controllers Filter by tenantId

- **外部コントローラ** (`omni-base` の `MqMessageController`)：テナント ID の取得に `@RequestHeader("X-Tenant-Id")` を使用します。すべてのクエリに `.eq(SysMqMessage::getTenantId, tenantId)` が含まれます。
- **内部コントローラ** (`omni-common-mqlog` の `MqMessageInternalController`)：Feign ベースのクロスサービス集約に `@RequestParam Long tenantId` を使用します。

### 3.4 Relay: No Tenant Filter (Intentional)

`MqMessageRelayService` は `tenant_id` に関係なく、すべての PENDING/FAILED メッセージをスキャンします。これは設計通りです — リレーはすべてのメッセージを配信する必要があるバックグラウンドインフラプロセスです。テナント分離はユーザー向けの読み書き操作にのみ適用されます。

## 4. Constraints & Pitfalls

### 4.1 tenantId Must Be Explicit

`ReliableMessageRelay.send()` に ThreadLocal または SecurityContext ベースのテナント解決を決して導入しないでください。明示的なパラメータが契約です — サイレントな NULL tenantId バグを防止します。

### 4.2 All Query Interfaces Must Filter

`sys_mq_message` をクエリするすべての新しいエンドポイントは `tenantId` フィルタを含む必要があります。例外なし — 「管理者」または「内部」エンドポイントであってもテナント境界を尊重する必要があります。

### 4.3 Idempotent DDL

`schema.sql` は安全で冪等なテーブル作成のため `CREATE TABLE IF NOT EXISTS` を使用します。`omni-common-mqlog` がクラスパスにある場合、サービス起動時にテーブルが自動作成されます。手動での DDL 実行は不要です。

### 4.4 MessageSender Strategy Pattern

新しい MQ ブローカ（例：Kafka）を追加するには：
1. `MessageSender` インターフェースを実装：`brokerType()` は `"kafka"` を返し、`send(SysMqMessage)` は実際の配信を処理します。
2. Spring Bean として登録 — `MqLogAutoConfiguration` はすべての `MessageSender` Bean を `brokerType` をキーとしたマップに収集します。
3. `ReliableMessageTemplate.send()` を呼び出す際に `broker_type = "kafka"` を設定します（または適切なバインディングを使用します）。

`MqMessageRelayService` やリレーロジックの変更は不要です。

## 5. New Service Onboarding (Tutorial)

新しいサービス（例：`omni-order`）に信頼性の高い MQ メッセージ送信を追加するには：

### Step 1: Add Dependency

`omni-order/pom.xml` に追加：

```xml
<dependency>
    <groupId>com.omni</groupId>
    <artifactId>omni-common-mqlog</artifactId>
    <version>${project.version}</version>
</dependency>
```

これにより `omni-common-core`（`ReliableMessageRelay` インターフェース用）と `omni-common-mybatis`（`SysMqMessageMapper` 用）が自動的に取り込まれます。

### Step 2: Table Auto-Creation

`schema.sql` は起動時に自動実行されます（`CREATE TABLE IF NOT EXISTS sys_mq_message`）。確認：

```sql
SELECT COUNT(*) FROM sys_mq_message;
```

### Step 3: Inject and Use

ビジネスサービスで：

```java
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final ReliableMessageRelay reliableMessageRelay;

    @Transactional
    public void createOrder(OrderDTO dto, Long tenantId) {
        // ... ビジネスロジック ...
        Order order = orderMapper.insert(entity);

        // Outbox に書き込み — 同じトランザクション、原子性を保証
        reliableMessageRelay.send("order-out-0", order, tenantId, "order:" + order.getId());
    }
}
```

### Step 4: Verify Relay Job Registration

サービスを起動し、XXL-JOB 管理コンソール（`http://localhost:18080`）を確認：
- エグゼキュータ：サービスの AppName がエグゼキュータリストに表示されるはずです
- タスク：`mqRelayHandler` が cron `0/10 * * * * ?` で登録されているはずです
- まだ実行中でない場合はタスクを開始してください

### Step 5: Check Frontend Admin UI

フロントエンドの `運用監視 → メッセージ記録` に移動：
- 新しいメッセージが `status = PENDING` (0) で表示され、リレー後に `SENT` (1) に遷移するはずです
- `tenantId`、`status`、`topic`、`serviceName`、または時間範囲でフィルタリング
- 失敗したメッセージの再送信またはデッドレターのスキップ

## 6. Extension Guide

### 6.1 Adding a New MQ Broker

`MessageSender` インターフェースを実装：

```java
@Component
public class KafkaMessageSender implements MessageSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Override
    public String brokerType() {
        return "kafka";
    }

    @Override
    public void send(SysMqMessage msg) {
        kafkaTemplate.send(msg.getTopic(), msg.getMsgKey(), msg.getPayload()).get();
    }
}
```

`MqLogAutoConfiguration` はすべての `MessageSender` Bean を自動収集します。リレーサービスは `broker_type` カラムでルーティングします — リレーコードの変更は不要です。

### 6.2 Customizing Retry Strategy

現在のリトライパラメータは `MqMessageRelayService` にハードコードされています：

| パラメータ | 場所 | デフォルト値 |
|-----------|----------|---------|
| `BATCH_SIZE` | `MqMessageRelayService` | 100 |
| `BACKOFF_BASE_SECONDS` | `MqMessageRelayService` | 10 |
| `max_retry` | `SysMqMessage.maxRetry` | 3 |

メッセージ単位でカスタマイズ：`ReliableMessageTemplate.send()` を呼び出す際に `maxRetry` を設定します（インターフェースの拡張が必要）。グローバルにカスタマイズ：`MqMessageRelayService` Bean をカスタムパラメータでオーバーライドします。

### 6.3 Customizing Relay Schedule

リレータスクの cron 式は XXL-JOB 管理コンソールで設定可能です。デフォルトは `0/10 * * * * ?`（10秒ごと）です。スループットとレイテンシの要件に応じて調整してください。

---

## 7. 技術選定の考察：Outbox パターン vs 直接送信

| 考慮事項 | Outbox パターン（Omni-Stack が採用） | 直接 MQ 送信 |
|------|------------------------------|------------|
| **トランザクション一貫性** | ビジネスデータとメッセージが同じ DB トランザクションに書き込まれ、原子性を保証 | 分散トランザクションの問題：DB コミット成功だが MQ 送信失敗、またはその逆 |
| **信頼性** | メッセージが DB に永続化され、サービス再起動後も配信を再開可能 | MQ 送信失敗時にメッセージが失われ、リトライ不可 |
| **結合度** | ビジネスコードは Outbox テーブルに書き込むのみで、MQ Broker の可用性に依存しない | ビジネスコードが MQ 接続に直接依存し、Broker 利用不可時にブロックされる |
| **レイテンシ** | 最大レイテンシ = リレータスクスケジュール間隔（10秒）+ 配信時間 | リアルタイム送信、最低レイテンシ |
| **複雑性** | Outbox テーブル + リレータスク + ステートマシンが必要 | シンプルだが、分散トランザクションの処理が必要 |
| **可観測性** | メッセージステータスの可視化（PENDING/SENT/FAILED/DEAD_LETTER） | MQ Broker のログのみ |

**結論**：Outbox パターンは少量のレイテンシと引き換えに強い一貫性と可観測性を実現し、メッセージ信頼性への要求が高いビジネスシナリオに適しています。

### 直接送信のリスクシナリオ

```
シナリオ 1：DB コミット前にサービスがクラッシュ
  直接送信：MQ は送信済みだが DB トランザクションが未コミット → コンシューマが「幽霊メッセージ」を受信
  Outbox：メッセージが Outbox に書き込まれていない → 送信されない

シナリオ 2：DB コミット後に MQ 送信が失敗
  直接送信：DB はコミット済み、MQ が失敗 → メッセージが失われる
  Outbox：メッセージが Outbox に書き込まれている（PENDING）→ リレータスクが配信をリトライ

シナリオ 3：MQ Broker がダウン
  直接送信：ビジネスコードがブロックまたは例外をスロー → ユーザー体験に影響
  Outbox：メッセージの Outbox 書き込みは影響なし → Broker 回復後に自動配信
```

## 8. RocketMQ Docker デプロイ設定ガイド

### コンテナ設定

```yaml
# docker-compose.yml
rocketmq-namesrv:
  image: apache/rocketmq:5.3.1
  container_name: omni-rocketmq-namesrv
  ports:
    - "9876:9876"
  command: sh mqnamesrv
  networks:
    - omni-network

rocketmq-broker:
  image: apache/rocketmq:5.3.1
  container_name: omni-rocketmq-broker
  ports:
    - "10911:10911"
    - "10909:10909"
  depends_on:
    - rocketmq-namesrv
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876
  command: sh mqbroker -n rocketmq-namesrv:9876
  networks:
    - omni-network
```

### 主要な設定詳細

| 設定項目 | 値 | 説明 |
|---------|-----|------|
| NameServer ポート | 9876 | RocketMQ サービスディスカバリポート |
| Broker ポート | 10911 | Broker メインポート |
| Broker VIP ポート | 10909 | Broker VIP チャネル（高速応答） |
| ネットワーク | omni-network | 他のサービスと同じ Bridge ネットワーク |

### Spring Cloud Stream 設定

```yaml
# application.yml
spring:
  cloud:
    stream:
      rocketmq:
        binder:
          name-server: ${ROCKETMQ_NAMESRV:rocketmq-namesrv:9876}
      bindings:
        oper-log-out-0:
          destination: omni-oper-log-topic
          content-type: application/json
```

**環境変数オーバーライド**：Docker デプロイ時に `SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER` 環境変数で NameServer アドレスをオーバーライドします。

## 9. トラブルシューティングガイド

| 問題 | 考えられる原因 | 確認方法 |
|------|---------|----------|
| **メッセージが PENDING のまま** | リレータスクが起動していない | XXL-JOB コンソールで `mqRelayHandler` が登録され起動しているか確認；cron 設定を確認 |
| **メッセージ送信失敗で FAILED に入る** | RocketMQ Broker が起動していない | RocketMQ コンテナの状態を確認；`spring.cloud.stream.rocketmq.binder.name-server` 設定が正しいか確認 |
| **メッセージが DEAD_LETTER に入る** | 最大リトライ回数（3回）を超過 | フロントエンド管理画面でメッセージ詳細とエラー情報を確認；問題を修正後、手動で再送信 |
| **コンシューマがメッセージを受信していない** | Topic が未作成またはコンシューマが未購読 | RocketMQ コンソールで Topic と Consumer Group を確認；コンシューマサービスが起動しているか確認 |
| **テナント分離が機能しない** | クエリが tenantId でフィルタリングされていない | Controller に `.eq(SysMqMessage::getTenantId, tenantId)` が含まれているか確認 |
| **重複配信** | リレータスクが同じメッセージを複数回スキャン | `msgId` のユニーク制約を確認；`StreamBridge.send()` の冪等性を確認 |
