# 4. プロジェクトでの実装：下書きを実行可能な版にする

編集画面の図を、まだ検討中の承認制度だと考えます。保存は記録を残す操作、公開は新しい申請への適用を認める操作です。下書き、公開版、エンジン定義を分けています。

## 作図と公開を分ける

modelKey は現在のテナント内で一意で、英字で始まり、その後は英字・数字・ハイフンを使えます。初期下書きの業務版は 0 です。保存時には設計用 JSON と BPMN XML を受け取ります。バックエンドが JSON だけから実行可能 XML を生成する実装はありません。

公開後の編集では独立した下書きを作り、公開済みの内容を残します。実行中の申請は元の定義を使います。業務版はモデル内の最大値に一を足し、Flowable の定義版はエンジンが管理します。伝票自身の同時更新用バージョンは別です。

## 一つ目のソース：ロックしてから下書きを確認する

WorkflowModelServiceImpl の公開メソッド冒頭です。

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
    public PublishResult publishModel(Long modelId, String userName) {
        // 1. SELECT FOR UPDATE 锁定模型行
        WfProcessModel model = lockModelForUpdate(modelId);

        // 2. 获取草稿版本
        WfProcessModelVersion draft = versionMapper.selectById(model.getCurrentDraftVersionId());
        if (draft == null || draft.getBpmnXml() == null || draft.getBpmnXml().isBlank()) {
            throw new BusinessException("BPMN XML 为空，无法发布");
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            throw new BusinessException("当前草稿指针指向非 DRAFT 版本(" + draft.getStatus()
                    + ")，请先通过 saveDraft 创建独立草稿");
        }
```

行ロックで同じモデルの公開を順番待ちにし、同じ下書きを二人が別々に公開することを防ぎます。XML が空、または DRAFT 以外なら拒否します。その後に XML 検証が続きます。ローカルトランザクション内の一行のロックであり、他の業務サービスも含む全体ロックではありません。

ロックの取得は直接 JDBC です。「ロックできた」と「テナントの利用権限を確認した」は別のことです。

## XML が読めるだけでは足りない

BpmnXmlValidator は DOCTYPE や外部実体などを無効にし、実行可能な process が一つ、process id と modelKey の一致、開始・終了、承認割り当て拡張、対象の回覧拡張、ゲートウェイの既定接続、一部の接続参照を検査します。

assignment JSON の業務上の正しさまでは証明しません。スクリプト、リスナー、式の完全な安全許可リストもありません。拡張要素があっても承認者が見つかるとは限らず、実際の役割、組織、業務場面で確認する必要があります。

## 二つ目のソース：テナントを明示して図を渡す

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java" -->
```java
            deployment = repositoryService.createDeployment()
                    .name(model.getModelName())
                    .category(model.getCategory())
                    .addString(model.getModelKey() + ".bpmn20.xml", bpmnXml)
                    .tenantId(effectiveTenantId)
                    .deploy();
```

XML 資源、名前、分類、有効なテナントを指定してデプロイします。モデルに分類がある場合は、XML の targetNamespace も置換し、エンジン定義の分類に使います。

その後、定義 ID、デプロイ ID、業務版を記録し、新版を PUBLISHED にし、前の公開版をアーカイブし、モデルの公開版参照を更新します。この一連のローカル処理が成功してから、新しい申請が明確な版を使うようにします。

## 公開失敗時は応答と実際の保存結果を見る

検証失敗の分岐では FAILED を書こうとしてから業務例外を投げます。しかし同じロールバック対象トランザクション内なので、**FAILED が必ず保存されるとは限りません**。失敗版が残ったと決めつけたり、状態を見ずに連打したりしないでください。

エラーから XML と現在の下書きを確認し、必要な修正を保存して再検証します。同時公開やタイムアウトでは、先に版一覧と公開版参照を更新して、すでに成功した公開がないか確認します。タイムアウトだけで未公開とは判断できません。

## 生の定義管理は別の入口

従来の定義管理にはデプロイ、BPMN 読み取り、停止、有効化、関連データを含む削除が残っています。上記の下書き検証や業務版公開を経由しません。現在、ID 指定操作に統一的なテナント帰属確認がありません。definition の機能権限だけをデータ分離の証拠にせず、通常のモデル公開の代替として使わないようにします。

次は[開始と担当者の選定](workflow-assignment.jp.md)を説明します。
