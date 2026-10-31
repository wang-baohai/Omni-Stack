# 4. 操作ログ：業務の記録と機密情報の保護

実行ログは技術的な処理を説明し、操作ログは業務の操作を記録します。@OperLogで収集箇所を宣言すると、アスペクトが必要情報と差分を集め、内容をサニタイズして生成側へ渡します。Authの認証イベントはsys_audit_logに記録し、操作ログモジュールを参照しません。

## 注釈で重複を減らせる理由

アスペクトは業務メソッドの前後に共通処理を挟みます。業務そのものは元のメソッドが行います。Springプロキシを経由する必要があり、同じインスタンス内の直接呼び出しでは自動的に働きません。スナップショット設定不足、読めないエンティティ、読み取れる変更がない場合は差分が空になることもあります。

## 検索画面までどう届くか

操作ログ生成側の送信メソッド全体です。

<!-- omni:source path="omni-backend/omni-common-operlog/src/main/java/com/omni/common/operlog/producer/OperLogProducer.java" -->
```java
    public void send(OperLogMessage message) {
        try {
            Long tenantId = message.getTenantId();
            if (reliableRelay != null) {
                reliableRelay.send(BINDING_NAME, message, tenantId, message.getEventId());
            } else {
                streamBridge.send(BINDING_NAME, message);
            }
        } catch (Exception e) {
            log.warn("操作日志：MQ 消息发送失败: {}", e.getMessage(), e);
        }
    }
```

信頼性メッセージの部品があればOutboxを優先し、部品がない場合だけ直接送信します。catchに注目してください。送信失敗は記録され、業務応答が自動的に失敗へ変わるわけではありません。Outboxへの書き込み失敗から直接送信へ切り替えることもありません。業務成功時に必ずログが残ることや、終了済みの業務トランザクションとコントローラー後処理が原子的であることは保証しません。

## 書き込み前に情報を隠す

サニタイザーが遮蔽対象とする実際の項目一覧です。

<!-- omni:source path="omni-backend/omni-common-operlog/src/main/java/com/omni/common/operlog/sanitize/OperLogSanitizer.java" -->
```java
    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "passwd", "pwd", "credential", "authorization", "token",
            "accesstoken", "refreshtoken", "secret", "clientsecret", "idcard",
            "identityno", "address", "content", "remark", "remarks", "note", "notes",
            "description", "customername", "contactname", "leadname", "companyname",
            "fullname", "opportunityname", "subject", "reason"
    );
```

電話番号、メール、一般的な資格情報の文字列も処理します。スナップショット、引数、例外は適切なサニタイズ経路を通す必要があります。新しい業務秘密をすべて自動認識できるわけではないため、項目を追加したらルールと拒否例も保守します。JSON形式だけではマスキングの証明になりません。

調査の便宜のためにパスワード、トークン、Cookie、Authorization、リクエストやメッセージの本文全体を記録しないでください。実行ログ、DB監査、画像、エクスポートはそれぞれ別の出口です。一つをサニタイズしても他が安全とは限りません。

次は[実際の画面と復旧](logging-practice.jp.md)を学びます。
