# ワークフローの開発とモデリング

`workflow`、`full`、または `omni-workflow` を含むプリセットが対象です。先に[ワークフロー操作](../guides/workflow-approval.jp.md)と Docker の準備を済ませます。現在の状態遷移、テナント、公開ロック、失敗の意味は[ワークフローの事実](../workflow.jp.md)とソースを参照します。

モデル操作には、操作に対応する `workflow:model:list`, `workflow:model:create`, `workflow:model:update`, `workflow:model:validate`, `workflow:model:publish` の権限が必要です。ログインだけでは操作できません。テナント内で固有の `modelKey` を選び、BPMN `<process id>` と一致させます。後片付けは、自作かつ参照がないことを確認した練習データだけに限定します。

## 4. 拡張ガイド

### 4.1 承認プロセスの種類を追加する

1. 各 UserTask に `<omni:assignment>` を設定し、既存のロール、アンカー、スコープを使って BPMN XML を設計します。
2. モデルを作成します：`POST /api/workflow/model`。
3. XML を保存します：`PUT /api/workflow/model/{id}/draft`。
4. 現在のドラフトを検証します：`POST /api/workflow/model/{id}/validate`。エラーと警告を確認します。
5. 公開します：`POST /api/workflow/model/{id}/publish`。公開処理の内部でも再検証します。

[BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) は XML、プロセス ID、ノード、必須拡張の存在を検証します。assignment JSON の各フィールドの意味まで完全に検証するものではありません。検証後も操作ガイドに従って実際に起動・承認し、候補者、失敗分岐、結果を確認します。既存機能の組み合わせは通常コード変更不要ですが、新しいアンカーや戦略は以下の拡張が必要です。

### 4.2 アンカーの種類を追加する

1. [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java) の `doResolveCandidates()/resolveAnchorUnitId()` から、新しいアンカーとテナント条件付き組織検索を実装します。
2. [ScopedRoleAssignmentListener](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java)、`CandidateResolverDelegate`、`CandidateResolverBean` の呼び出し境界を確認します。リスナーだけの修正では共有解析を変更できません。
3. [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue) の選択肢、パラメーター、XML 保存と再読込を更新し、[useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts) も確認します。
4. 追加のフィールド検証は明示的に実装・テストします。[BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java) に完全なアンカー値一覧や JSON Schema 検証があるとは想定しません。

### 4.3 フォールバック戦略を追加する

1. [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java) の `handleFallback()` と `escalateToParentForCandidates()` を確認し、各エンジン入口へ統合します。
2. 既存の `ASSIGN_ADMIN` は現在のテナントの組織単位 `1` で `SUPER_ADMIN` 候補を検索し、不在なら失敗します。`ESCALATE_PARENT` は親組織で同じロールとスコープを再検索し、空なら失敗させます。
3. 親への遡及分岐は ExecutionListener と `CandidateResolverBean` にあります。現在の `CandidateResolverDelegate` は `handleFallback()` を直接呼び、外側の catch は例外をログに記録するだけです。3経路を同一視せず、使用する入口の実際の失敗結果を確認します。
4. フロントエンドの戦略選択肢を更新し、3入口それぞれで候補者あり・なし、テナント境界、依存先障害をテストします。

### 4.4 通知サービスを実装する

現在の [WorkflowNotificationService](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java) の待ちタスク通知メソッドは、`processInstanceId` を含む4引数です。

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/notification/WorkflowNotificationService.java" -->
```java
void notifyPendingTask(String userId, String taskId, String processInstanceId, String title);
```

次の実装は `omni-workflow` 内の `com.omni.workflow.notification` パッケージに置き、コンポーネントスキャンで登録します。イベントをログへ記録するだけで、外部通知は送りません。実際の送信、失敗処理、再試行は業務側で実装します。

```java
package com.omni.workflow.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.omni.common.workflow.notification.WorkflowNotificationService;

/** 演示通知 SPI 的完整签名；只记录事件，不发送外部通知。 */
@Slf4j
@Service
public class MyNotificationService implements WorkflowNotificationService {

    /** 记录待办通知事件，真实投递由业务实现。 */
    @Override
    public void notifyPendingTask(String userId, String taskId,
                                  String processInstanceId, String title) {
        log.debug("待办通知事件：taskId={}, processInstanceId={}", taskId, processInstanceId);
    }

    /** 记录任务通知清理事件。 */
    @Override
    public void clearPendingTask(String taskId) {
        log.debug("清理待办通知事件：taskId={}", taskId);
    }
}
```

[FlowableAutoConfiguration](../../omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/config/FlowableAutoConfiguration.java) は `@ConditionalOnMissingBean` で `NoOpNotificationService` を提供します。独自の `WorkflowNotificationService` Bean を登録したら条件を確認し、空実装やログを配信成功と扱わないでください。

### 4.5 CC 通知

ServiceTask に `flowable:delegateExpression="${ccNotifyDelegate}"` と `<omni:cc>` を設定します。[CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java) はユーザー ID またはロール・組織から受信者を解決し、`wf_cc_record` へ保存します。CC は承認待ち一覧へ入らず、待ちタスク通知 SPI も呼びません。

この JavaDelegate は同期実行されます。現在は例外を捕捉してログに記録します。CC 記録がない場合は設定、SQL ログ、トランザクション結果を確認してください。処理の継続だけでは CC 成功や非同期配信を証明できません。

## 5. 現在のエンジンと設計境界

エンジン依存は [Maven POM](../../omni-backend/pom.xml) の `flowable.version` と[共有 Starter POM](../../omni-backend/omni-common-workflow/pom.xml) で定義します。自動構成、テナント連携、承認 SPI は[ワークフローの事実](../workflow.jp.md)、デザイナー依存は[フロントエンド package.json](../../omni-frontend/package.json) を参照します。アップグレードやエンジン変更では、連携点、公開済みモデル、多重インスタンスの回帰を確認し、設計上の取捨選択は ADR に記録します。

## 6. BPMN モデリング規約

### 命名規約

| 要素 | 規則 | 例 |
|---|---|---|
| Process ID | `model_key` と一致 | `leave-request`, `expense-approval` |
| UserTask ID | kebab-case でロールと操作 | `dept-leader-approve`, `hr-review` |
| SequenceFlow ID | `flow-{source}-{target}` | `flow-start-submit` |
| Gateway ID | `{type}-gw-{purpose}` | `exclusive-gw-amount`, `parallel-gw-notify` |

### モデリング原則

1. 各 UserTask に `<omni:assignment>` を設定して候補者を動的に解決し、個人アカウントを固定しません。
2. ExclusiveGateway に無条件の default flow を設定し、他条件が一致しない場合に使用します。
3. 多重インスタンスの完了条件は次の式です。`||` を Markdown のエスケープなしでコピーできます。 `${rejectedCount > 0 || approvedCount >= requiredApprovals}`
4. CC は前述の ServiceTask デリゲートを使い、実際の記録で結果を確認します。
5. 公開前に `BpmnXmlValidator` の結果を読み、承認・拒否・候補者なしの分岐も実際に検証します。

### デザイナーのソース入口

| ファイル | 責務 |
|---|---|
| [useBpmnModeler.ts](../../omni-frontend/src/composables/useBpmnModeler.ts) | Modeler の作成と破棄 |
| [useBpmnExtension.ts](../../omni-frontend/src/composables/useBpmnExtension.ts) | BPMN 拡張の読み書き |
| [bpmnContextPadI18n.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadI18n.ts) | コンテキストメニューの国際化 |
| [bpmnContextPadProvider.ts](../../omni-frontend/src/composables/workflow/bpmnContextPadProvider.ts) | コンテキストメニューの拡張 |
| [UserTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/UserTaskPanel.vue) | ロール・アンカー・スコープ・戦略 |
| [ServiceTaskPanel.vue](../../omni-frontend/src/components/workflow/panels/ServiceTaskPanel.vue) | CC パラメーター |
| [GatewayPanel.vue](../../omni-frontend/src/components/workflow/panels/GatewayPanel.vue) | ゲートウェイ条件 |

## 7. トラブルシューティング

| 現象 | 確認する入口と内容 |
|---|---|
| モデル公開失敗 | validate のエラー、[BpmnXmlValidator](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/engine/BpmnXmlValidator.java)、現在のドラフトを確認します。 |
| 候補者が空 | [CandidateResolutionService](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/CandidateResolutionService.java) で roleCode、アンカー、scopeMode、テナント、有効状態を追い、実際のフォールバック分岐を確認します。 |
| インスタンスを起動できない | 操作ガイドで公開版・権限・応答を調べ、`wf_process_model_version.status=PUBLISHED` を確認します。 |
| 複数承認が終了しない | この MI 活動ルートの `approvedCount/rejectedCount/requiredApprovals` と completionCondition を確認します。プロセス変数だけでは不足します。 |
| deleteReason が想定外 | [ワークフローの事実](../workflow.jp.md) §3.1 を参照します。`MI_END` の自動終了と `deleted` の終了処理は異なります。 |
| テナント分離異常 | 信頼するゲートウェイで認証した要求と `TenantInfoFilter/TenantInfoHolder` の設定・解放を確認します。クライアントの自己申告ヘッダーだけを認証済み ID と扱いません。 |
| CC 記録がない | [CcNotifyDelegate](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/delegate/CcNotifyDelegate.java) の設定解析、SQL、例外ログを調べます。プロセス継続だけでは CC 成功になりません。 |
| デザイナーを読み込めない | package.json とロックファイルに従って依存を導入し、コンテナのフロントエンドとブラウザーコンソールを確認します。 |

ソース拡張後はローカル Docker の Maven Wrapper でバックエンドを検証し、デザイナー変更時はフロントエンド build/lint も実行します。完全な操作、期待結果、後片付けは[ワークフロー操作](../guides/workflow-approval.jp.md)を参照します。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run build
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm frontend npm run lint
```
