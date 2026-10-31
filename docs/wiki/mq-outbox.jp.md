# 3. Outbox：送信予定を先に確実に保存する

Outboxは業務サービス自身の発送待ち一覧です。業務と一緒に保存するのは送信の意図であり、到着の証明ではありません。各サービスにローカルsys_mq_messageを持ち、正式な表構造はLiquibase移行で管理します。起動のたびに任意に表を作る方式ではありません。

## 手順1：トランザクションとテナントを明示する

現在のテンプレートの宣言とテナント検証です。抜粋はテナント検証の末尾までです。

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java" -->
```java
    @Transactional(propagation = Propagation.REQUIRED)
    public void send(String bindingName, Object payload, Long tenantId, String msgKey) {
        if (tenantId == null || tenantId <= 0) {
            throw new IllegalArgumentException("可靠消息 tenantId 必须为正整数");
        }
```

REQUIREDは既存のトランザクションに参加し、なければ自分のものを開始します。業務との原子性には同じデータソースの有効な業務トランザクションとSpringトランザクションプロキシが必要です。DBをまたぐ書き込み、終了後の後処理、直接の内部呼び出しでは代用できません。

tenantIdは正の整数を明示的に渡し、ThreadLocalから推測しません。中継は元のリクエストスレッドがなくなった後に複数テナントを扱うため、メッセージ自身が正しいテナントを持ちます。

## 手順2：初期状態を記録する

挿入前の実際の代入です。

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/template/ReliableMessageTemplate.java" -->
```java
        message.setBrokerType("rocketmq");
        message.setStatus(SysMqMessage.STATUS_PENDING);
        message.setRetryCount(0);
        message.setMaxRetry(3);
        message.setServiceName(serviceName);
        message.setProducerTraceId(MDC.get("traceId"));
        message.setTenantId(tenantId);
        message.setCreateTime(LocalDateTime.now());

        sysMqMessageMapper.insert(message);
```

PENDING、失敗回数0、既定の最大失敗回数3で始まります。serviceNameが出所を、producerTraceIdが利用可能な関連情報を残します。挿入成功は一覧への保存だけを意味します。

msgIdはこの記録の番号です。msgKeyは調査用の業務キーで、自動的な一意の冪等制約ではありません。sendを繰り返すと別の記録を作り得るため、業務入口にはリクエストの冪等性が必要です。

## 手順3：コミットと配送を別に確認する

正当な業務トランザクションで、ロールバック時には業務変更も送信待ち記録も残らず、コミット時には記録が存在することを確認します。その後、中継、消費処理、重複消費を別に検証します。演習行を一件挿入しただけでは業務トランザクションの原子性は証明できません。

次は[取得、再試行、消費](mq-relay.jp.md)です。
