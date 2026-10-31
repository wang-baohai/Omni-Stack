# 3. フレームワークの仕組み：「完了」の後に Flowable がすること

対象はプロジェクトが使う Flowable 8.0.0 です。公式ソース配布物から連続した短い部分を原文のまま引用しています。すべての Java 型を覚えるより、前後の説明を読んでください。

## 第一段階：操作を共通の実行入口へ渡す

TaskServiceImpl の 234–237 行です。

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-command.java.txt" -->
```java
    @Override
    public void complete(String taskId) {
        commandExecutor.execute(new CompleteTaskCmd(taskId, (Map<String, Object>) null));
    }
```

タスク ID を受け取り、「このタスクを完了する」操作を CompleteTaskCmd にして commandExecutor に渡します。共通窓口へ処理票を渡すイメージです。まだ「購入を承認する」というプロジェクト固有の判断ではありません。

CompleteTaskCmd はその後 TaskHelper.completeTask を呼びます。変数、完了リスナー、履歴、イベントなどを処理しますが、現在のユーザーに承認権限があると証明したわけではありません。プロジェクト側の操作条件確認が必要です。

## 第二段階：タスクを完了し、次の実行を予定する

TaskHelper の 178–184 行です。

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/task-advance.java.txt" -->
```java
        completeTask(taskEntity, userId);

        // Continue process (if not a standalone task)
        if (taskEntity.getExecutionId() != null && !bpmnErrorPropagated) {
            ExecutionEntity executionEntity = processEngineConfiguration.getExecutionEntityManager().findById(taskEntity.getExecutionId());
            CommandContextUtil.getAgenda(commandContext).planTriggerExecutionOperation(executionEntity);
        }
```

最初の呼び出しでタスクを完了します。続いて、プロセスの実行に属し、該当する BPMN エラーを伝播していない場合に、処理を進める操作を予定します。

完了は一行の状態変更だけではありません。ゲートウェイへ進み、次のタスクを作り、場合によってはプロセスが終了します。プロジェクトが complete の前に意見や票数を書くのは、後続処理が今回の結果を読めるようにするためです。予定されたからといって、モデル内のすべての仕事が同じリクエスト内で終わるわけではありません。

## 第三段階：複数人の承認をいつ終えるか

同じノードで複数人に個別タスクを作る仕組みがマルチインスタンスです。ParallelMultiInstanceBehavior の 231–234 行から、離脱呼び出しまでを引用します。

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/parallel-completion.java.txt" -->
```java
            boolean isCompletionConditionSatisfied = completionConditionSatisfied(execution.getParent());
            if (nrOfCompletedInstances >= nrOfInstances || isCompletionConditionSatisfied) {
                leave(executionEntity, nrOfInstances, nrOfCompletedInstances, isCompletionConditionSatisfied);
```

全インスタンスが完了したか、モデルの完了条件が成立したかを「または」で確認します。プロジェクトの「否認票がある、または必要な承認票が集まった」という条件なら、全員が操作する前にノードを終える場合があります。

条件式の結果は真か偽でなければなりません。必要票数をフレームワークが会社に代わって決めるわけではありません。ALL、ANY、プロジェクトの票数はタスク編でつなげます。

## 第四段階：未処理のタスクが消える理由

MultiInstanceActivityBehavior の 266–269 行です。

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/multi-instance-cleanup.java.txt" -->
```java
        ExecutionEntityManager executionEntityManager = CommandContextUtil.getExecutionEntityManager();
        Collection<String> executionIdsNotToSendCancelledEventsFor = execution.isMultiInstanceRoot() ? null : Collections.singletonList(execution.getId());
        executionEntityManager.deleteChildExecutions(multiInstanceRootExecution, null, executionIdsNotToSendCancelledEventsFor, DELETE_REASON_END, true, flowElement);
        executionEntityManager.deleteRelatedDataForExecution(multiInstanceRootExecution, DELETE_REASON_END, false);
```

マルチインスタンスの実行と関連データを整理し、理由として DELETE_REASON_END を渡します。このクラスでは値を MI_END と定義しています。早期終了条件に達すると、投票していない人のタスクもこの理由で終了することがあります。

**規則に従い待機を終えたのであり、その人が賛成票を投じたのではありません。** 旧表示の auto-approved という名前でも、この事実は変わりません。

## デプロイもコマンドを通す

RepositoryServiceImpl の 98–100 行です。

<!-- omni:source path="docs/reference/upstream/flowable-8.0.0/deployment-command.java.txt" -->
```java
    public Deployment deploy(DeploymentBuilderImpl deploymentBuilder) {
        return commandExecutor.execute(new DeployCmd<Deployment>(deploymentBuilder));
    }
```

デプロイはモデル資源をエンジンに渡します。プロジェクトの公開には、モデルのロック、下書き検証、業務版の記録、公開先の更新も必要です。デプロイだけで公開全体が完了したり、別の業務サービスに伝票ができたりするわけではありません。

## このソースが示す範囲

コマンド受理、実行継続、複数人ノードの処理を説明しています。プロジェクトが公開していないタスクの引き受け（claim）、指定ノードへの差し戻し、実行中インスタンス移行の画面があることや、ローカル承認の成功を示すものではありません。

ソース配布物は公式 SHA-256 と照合済みです。元のパス、行、ハッシュ値、著作権表示は[出典記録](../reference/upstream/flowable-8.0.0/provenance.json)、完全なライセンスは [Apache 2.0](../reference/upstream/flowable-8.0.0/LICENSE.txt) に保存しています。[Flowable 公式 BPMN 説明](https://www.flowable.com/open-source/docs/bpmn/ch07b-BPMN-Constructs/)は任意の参考資料です。本文の理解に外部移動は必要ありません。

次は[プロジェクトのモデル公開](workflow-models.jp.md)です。
