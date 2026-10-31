# 4. プロジェクトでの実践：保守処理と個人の通知を分ける

管理者はシステムジョブを、ユーザーは統合ワークスペースで自分のジョブを管理します。実行器は共通でも、帰属の判断は同じではありません。

## システムジョブ：二つのアノテーションが必要な理由

XXL-JOB には呼び出す方法を、プロジェクトには名称、既定周期、引数の説明を伝える必要があります。そのため両方のアノテーションを確認します。

<!-- omni:source path="omni-backend/omni-common-job/src/main/java/com/omni/common/job/SystemJobRegistry.java" -->
```java
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                SystemJobMeta meta = AnnotationUtils.findAnnotation(method, SystemJobMeta.class);
                if (xxlJob != null && meta != null) {
                    String handlerName = xxlJob.value();
                    SystemJobInfo info = new SystemJobInfo();
```

実行用アノテーションだけではプロジェクトのシステム一覧への掲載を保証せず、説明用だけでは実行可能になりません。自動登録は不足分のみを追加し、既存の Cron と引数を保持するため、再起動で管理者の変更を上書きしません。実行器が無効なら、一覧にあっても実行中とは限りません。

MQ 中継とログのアーカイブは保守ジョブです。個人ジョブの共通入口は実行用アノテーションだけを持ち、一覧に出すためにシステム用メタデータを付けてはいけません。

## 個人ジョブ：時間割は個別、入口は共通

センターには個人ジョブごとの項目がありますが、呼ぶのはすべて userJobExecuteHandler です。メッセージにはローカル ID、テナント、種別、名称、引数が入り、共通入口が解析して種別から処理器を探します。

実際の検索は次の短いコードです。

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobHandlerRegistry.java" -->
```java
    public UserJobHandler getHandler(String jobType) {
        return handlers.get(jobType);
    }
```

Spring が注入する Map のキーは Bean 名です。Task-00001 なら完全に同名の Bean が必要で、「水分補給」の表示名ではルーティングしません。種別の行を追加するだけでは処理器は配置されません。

## 「マイジョブ」へ結果が届くまで

共通入口はメッセージのテナントを明示して個人ログを書き、最終実行時刻を更新し、XXL-JOB に結果を設定します。中心となる保存部分は次のとおりです。

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/job/UserJobExecuteHandler.java" -->
```java
        SysUserJobLog logEntity = new SysUserJobLog();
        logEntity.setJobId(jobId);
        logEntity.setTenantId(message.getTenantId());
        logEntity.setJobName(message.getJobName());
        logEntity.setJobType(jobType);
        logEntity.setFireTime(fireTime);
        logEntity.setExecuteTimeMs(executeTimeMs);
        logEntity.setStatus(status);
        logEntity.setErrorMessage(errorMsg);
        logEntity.setResultMessage(resultMessage);
        sysUserJobLogMapper.insert(logEntity);
```

この断片だけで全体成功は保証できません。引数の解析失敗はログ保存より前に終了し、DB 書き込みも失敗し得ます。個人ログと最終時刻更新は一つの宣言的トランザクションではありません。ログがない場合はネイティブ実行ログも調べ、成功時刻を作りません。

## 他人の番号を知っても操作できない理由

変更、削除、切り替え、実行、ログ取得の前に親ジョブを読み、現在のテナントと作成者を両方照合します。

<!-- omni:source path="omni-backend/omni-base/src/main/java/com/omni/base/controller/MyJobController.java" -->
```java
    private void verifyOwnership(Long jobId, Long tenantId, String username) {
        SysUserJob job = userJobService.getJobById(jobId);
        if (!tenantId.equals(job.getTenantId()) || !username.equals(job.getCreateBy())) {
            throw new BusinessException(403, "无权操作他人的任务");
        }
    }
```

メニュー権限より細かい検査です。現行ログサービスは検査済み親の jobId で問い合わせるため、すべての SQL に別途テナント条件を加えているとは説明できません。バックグラウンド処理もブラウザの HTTP スレッドに残るテナント情報へ依存しません。

## 登録失敗後も復旧の対応関係を守る

通常はローカル保存、遠隔登録、遠隔 ID の保存の順です。登録エラーには未コミットと、コミット後に応答だけが消えた場合があります。名前で推測せず、実行器グループ、handler、テナントとローカル ID を含む引数で照合します。

遠隔残存がないと確認してからローカル行を削除します。照会やクリーンアップも失敗したら関連を残してエラーを返します。その後の周期変更、開始停止、削除は遠隔操作を先に行い、ローカルを更新します。再起動や再作成は照合の代わりになりません。

次の[実際の管理画面](distributed-jobs-admin.jp.md)で、各段階と一覧・ログを対応させます。
