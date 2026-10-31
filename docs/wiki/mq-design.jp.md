# 2. フレームワークの選択：各段階を誰が担当するか

本工程はRocketMQを中継所とし、Spring Cloud Streamのbindingでコードと宛先をつなぎ、OutboxとXXL-JOBで中継処理を行います。これは現在の選択の説明で、すべてのMQ製品の能力比較や保証ではありません。

## 層を分ける理由

Spring Cloud Streamは共通の送信インターフェースを提供します。bindingはアプリ側の通路名、destinationは設定された実際の宛先です。現在のtopic項目にはbindingNameを保存します。Brokerの最終的なTopic名と決めつけず、対象サービスのStream設定を確認します。

Outboxはローカルコミットと後続の送信の間を補い、RocketMQが受け取りと配送を、消費側が業務処理を担当します。一つの段階の成功だけでは他の段階の成功を証明できません。

## 実際の送信メソッドを読む

現在のRocketMQ送信戦略のメソッド全体です。

<!-- omni:source path="omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/sender/RocketMqMessageSender.java" -->
```java
    public void send(SysMqMessage message) {
        Message<String> outbound = MessageBuilder.withPayload(message.getPayload())
                .setHeader(MESSAGE_ID_HEADER, message.getMsgId())
                .setHeaderIfAbsent(PRODUCER_TRACE_HEADER, message.getProducerTraceId())
                .build();
        boolean success = streamBridge.send(message.getBindingName(), outbound);
        if (!success) {
            throw new RuntimeException("StreamBridge 发送返回 false，binding=" + message.getBindingName());
        }
        log.debug("MQ 消息投递成功：msgId={}, binding={}", message.getMsgId(), message.getBindingName());
    }
```

MessageBuilderが本文と関連ヘッダーを組み立て、StreamBridgeがbindingNameに設定された通路へ送ります。falseなら例外にして中継の失敗処理へ進みます。成功しても消費側の業務コミットは未確認です。SENTは送信側の成功を表します。

omniMessageIdはOutbox記録を、omniProducerTraceIdは生成時のログを探す手掛かりです。権限の資格情報でも自動的な業務重複排除でもありません。生成時の追跡番号がない履歴も送信できます。

## 中継と送信戦略を残す理由

中継は取得、状態、待機、デッドレターをまとめて扱い、送信戦略は実際の送信機構を扱います。現在の保存テンプレートはbroker_typeをrocketmqに固定します。他のBrokerにはMessageSenderだけでなく、保存契約、binding設定、失敗処理、検証も必要です。クラスを一つ追加しただけでは切り替え完了ではありません。

次は[Outboxとトランザクション](mq-outbox.jp.md)を見ます。
