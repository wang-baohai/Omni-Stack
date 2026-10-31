# 4. 中継と消費：再試行と重複の両方に備える

XXL-JOBのmqRelayHandlerは既定で約10秒ごとに実行されます。時刻が到来したPENDING/FAILEDと、リース切れのPROCESSINGを最大100件ずつ探します。周期は配達期限ではなく、滞留、故障、待機があれば時間は延びます。

## 先に取得権を確保する

検索は候補の発見にすぎません。実際の取得処理は次の通りです。

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java" -->
```java
        int claimed = sysMqMessageMapper.claimForRelay(msg.getId(), LocalDateTime.now(),
                LocalDateTime.now().plusSeconds(CLAIM_LEASE_SECONDS));
        if (claimed == 0) {
            // 抢占失败：消息已被其他 executor 持有（租约未到期）或状态已变化，本轮直接跳过。
            log.debug("MQ 中继：抢占失败（已被其他执行流持有或状态已变），跳过 msgId={}", msg.getMsgId());
            return;
        }
```

条件付き更新に成功した実行だけが続行できます。PROCESSINGのリースは120秒です。CASは「まだ想定した状態なら変更する」という意味です。成功・失敗の書き戻しにも条件を付け、古い実行者が新しい結果を上書きしないようにします。

リースは競合を減らしますが、重複をなくしません。Brokerが受理した後、SENT保存前にプロセスが止まれば、リース切れ後に再送され得ます。少なくとも一回の配送を目指すため、消費側の冪等性が必要です。

## 失敗しても連続送信しない理由

通常の失敗分岐の待機計算です。

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java" -->
```java
            int backoffSeconds = (int) Math.pow(2, newRetryCount) * BACKOFF_BASE_SECONDS;
            LocalDateTime nextRetryTime = now.plusSeconds(backoffSeconds);
            sysMqMessageMapper.casApplyFailure(msg.getId(), SysMqMessage.STATUS_FAILED,
                    nextRetryTime, sanitized, now);
```

既定のmaxRetry=3では、1回目の失敗は20秒、2回目は40秒待ち、3回目でDEAD_LETTERとなり80秒待機は設定しません。指定時刻になっても次の実行待ちがあるため、「10秒以内に必ず届く」とは言えません。不明なbroker_typeも失敗状態遷移の処理へ進み、偽のPENDINGに残しません。

## 送信後に消費側が失敗する場合

Baseの操作ログ消費入口全体です。

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/consumer/OperLogConsumer.java" -->
```java
    @Bean
    public Consumer<OperLogMessage> operlogConsumer() {
        return message -> {
            try {
                operLogService.save(message);
            } catch (DuplicateKeyException e) {
                log.debug("操作日志事件已消费，忽略重复消息: {}", message.getEventId());
            } catch (Exception e) {
                log.error("操作日志消费失败，交由消息中间件重试: {}", e.getMessage(), e);
                throw e;
            }
        };
    }
```

eventIdの一意制約で重複挿入を防ぎ、DuplicateKeyExceptionだけを処理済みと扱います。他の例外は再送のためにBrokerへ伝えます。中継の再試行と消費側の再試行は別の段階です。SENTだけでは最終記録の保存は証明できません。

次は[管理画面と安全な復旧](mq-practice.jp.md)です。
