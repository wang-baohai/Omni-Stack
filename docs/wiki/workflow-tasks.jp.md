# 6. プロジェクトでの実装：承認、否認、複数人の票を数える

候補者は規則で選んだ集合、担当者はタスクの assignee、承認記録の人は実際に処理した人です。設計画面が作るマルチインスタンスのタスクは直接割り当てられ、担当者が引き受ける前の共有タスクプールはありません。

## 最初に「今、自分のタスクか」を確かめる

完了、承認者追加、削除、委任には、それぞれ workflow:approval:complete、add-signer、remove-signer、delegate の機能権限が必要です。さらに現テナントのタスクを検索し、本人が正確に担当者と一致することを確認します。

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImpl.java" -->
```java
    private Task requireCurrentAssigneeTask(String taskId, Long userId, Long tenantId) {
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .taskTenantId(String.valueOf(tenantId))
                .singleResult();
        if (task == null) {
            throw new BusinessException(409, "审批任务已被处理或不存在，请刷新待办列表");
        }
        if (!String.valueOf(userId).equals(task.getAssignee())) {
            throw new BusinessException(403, "无权操作该审批任务");
        }
        return task;
    }
```

存在しない、または他テナントで見つからない場合は業務コード 409 で、一覧を更新します。同テナントでも他人の担当なら 403 です。連打や管理者役割だけで、代理承認はできません。

## 承認・否認は、まず一人の一票

approved は今回の判断、comment は意見です。意見とタスク固有の結果を保存し、プロセス根の approved にも書きます。根の値は後の票で上書きされるため、全員の集計には使えません。

否認は同じ完了入口で approved=false にします。前のノードへ自動では戻りません。終了か別経路かはモデルの条件と接続によります。

## 複数人には共有の集計場所が必要

マルチインスタンス根を今回の会議の集計帳、各タスクを各人の投票用紙と考えます。最大 32 階層上がり、根固有の nrOfInstances を目印に探します。見つからないと従来定義の互換更新を残します。

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        if (miRoot != null) {
            runtimeService.setVariableLocal(miRoot, counterKey, currentCount + 1);
        } else {
            runtimeService.setVariable(execId, counterKey, currentCount + 1);
        }
```

賛成は approvedCount、否認は rejectedCount を増やし、**その後** taskService.complete を呼びます。設計画面が作る終了条件は次です。

```text
${rejectedCount > 0 || approvedCount >= requiredApprovals}
```

既定の ALL は全候補人数を閾値にし、ANY は 1 です。この式では一票の否認でもノードを離れます。手書き BPMN は適切な終了条件と否認経路を自分で用意します。ALL/ANY の文字だけで業務規則すべてが伝わるわけではありません。

追加 variables はこれらの後にタスクとプロセスへ書かれ、集計や経路の予約キーを除外していません。重要変数が絶対に上書きされないとは保証できません。拡張時は許可リストと不正入力の拒否テストを設計します。

## 承認者の追加・削除・委任で何が変わるか

| 操作 | 現在の実装 | 制限 |
|---|---|---|
| 追加 | 現マルチインスタンスに実行を追加し userId を渡す | 現担当者が操作。モデル変数の一致が必要で、通常の単独ノードにも使えるとは限らない |
| 削除 | 同じインスタンス・ノード・対象者の未処理タスクを探して実行を削除 | 済んだ投票は取消不可。同人の一致タスクが複数なら singleResult が失敗 |
| 委任 | タスクの assignee を変更 | 直接担当変更で、完了後に元担当へ戻る流れではない |

変更後の ALL 閾値は「承認済み票数 + 残る未処理タスク数」です。ANY と根が見つからない場合は再計算しません。追加自体は承認意見を残さず、新規タスクは投票済みではありません。

削除は deleteMultiInstanceExecution(..., false) を呼びます。**引数名は executionIsCompleted で、false は親のマルチインスタンスで完了扱いに数えない意味です。連鎖削除のスイッチではありません。** 入れ子の実際の動作は別途検証が必要です。

委任の中心は次の一行です。

<!-- omni:source path="omni-backend/omni-common-workflow/src/main/java/com/omni/common/workflow/approval/ApprovalServiceImpl.java" -->
```java
        taskService.setAssignee(taskId, targetUserId);
```

Flowable の delegateTask/resolveTask による返却処理は使っていません。追加・削除・委任の対象 ID は数字として解析するだけで、存在、有効状態、テナントを完全には検査していません。ボタンと機能権限だけで対象本人の検証済みとは言えません。

## 本人が押していないのに終了した理由

ANY で承認が出た場合など、早期終了条件に達すると残りが MI_END で終わることがあります。記録では MI で始まる削除理由を旧ラベル auto-approved、それ以外を cancelled とします。**前者はシステムによる省略であり、その人の賛成票ではありません。**

通常終了はタスク固有の approved で判定し、なければ互換処理で承認扱いです。意見は最初の一件で、最新保証はありません。表示の判定と実際の投票を分けて読みます。

## 終了操作と、まだ公開していない操作

通常の強制終了には workflow:instance:terminate、同テナント、起案者本人、実行中という条件があります。実行状態を削除する操作で、後で再開する一時停止ではありません。内部業務終了は別の信頼済みサービス経路で、対象のサービス間連携経路では CANCELLED を書きます。

独立した claim/unclaim、ワークフロー取り下げ、別の転送操作、委任後返却、指定ノードへの差し戻し、実行中の版移行、タスク停止・有効化、管理者代理承認・代理終了の入口はありません。定義の停止は特定タスクの停止とは別です。業務画面の取り下げが内部終了を使っても、汎用取り下げ API にはなりません。

最後に[結果と障害調査](workflow-events.jp.md)を読みます。
