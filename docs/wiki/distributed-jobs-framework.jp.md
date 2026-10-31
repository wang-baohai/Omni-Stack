# 3. 内部を読む：時間割が一回の実行になるまで

当直室が仕事を割り当てる流れで考えます。時刻が近い仕事を探し、割り当てを記録し、担当者を選び、仕事を送り、結果を受け取ります。以下は 3.3.1 の連続した短い原文です。

## 第一段階：センターを調整し、近い時刻を調べる

複数のセンターが動く場合は走査の調整が必要です。この部分はトランザクションを開始し、スケジューリングロックを取得し、近い将来の対象を問い合わせます。このロックは走査の調整であり、任意の業務が厳密に一回だけ実行される保証ではありません。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/schedule-lock.java.txt" -->
```java
                        transactionStatus = XxlJobAdminBootstrap.getInstance().getTransactionManager().getTransaction(new DefaultTransactionDefinition());
                        // 1、job lock
                        String lockedRecord = XxlJobAdminBootstrap.getInstance().getXxlJobLockMapper().scheduleLock();
                        long nowTime = System.currentTimeMillis();

                        // scan and process job
                        List<XxlJobInfo> scheduleList = XxlJobAdminBootstrap.getInstance().getXxlJobInfoMapper().scheduleJobQuery(nowTime + PRE_READ_MS, preReadCount);
```

後続は時刻を逃したか、先読み範囲かを判断し、即時実行または時間輪への登録を行い、次回時刻を更新します。時刻を逃した場合は設定済みの misfire 方針で処理し、過去の全回数を無制限に補うわけではありません。

## 第二段階：今回の実行を封筒に入れる

先にスケジューリングログ ID を作り、ジョブ、ハンドラー、引数、タイムアウト、ログとの対応を送信内容に入れます。実行器は名称から推測せず、今回の入力を受け取ります。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/trigger-envelope.java.txt" -->
```java
        // 2、init trigger-param
        TriggerRequest triggerParam = new TriggerRequest();
        triggerParam.setJobId(jobInfo.getId());
        triggerParam.setExecutorHandler(jobInfo.getExecutorHandler());
        triggerParam.setExecutorParams(jobInfo.getExecutorParam());
        triggerParam.setExecutorBlockStrategy(jobInfo.getExecutorBlockStrategy());
        triggerParam.setExecutorTimeout(jobInfo.getExecutorTimeout());
        triggerParam.setLogId(jobLog.getId());
        triggerParam.setLogDateTime(jobLog.getTriggerTime().getTime());
        triggerParam.setGlueType(jobInfo.getGlueType());
        triggerParam.setGlueSource(jobInfo.getGlueSource());
        triggerParam.setGlueUpdatetime(jobInfo.getGlueUpdatetime().getTime());
        triggerParam.setBroadcastIndex(index);
        triggerParam.setBroadcastTotal(total);
```

この後、実行器グループのオンラインアドレスとルーティング方針で送信先を選びます。アドレスがなければ送れません。選択できても接続とハンドラーの存在確認は別に必要です。

## 第三段階：サービスができる仕事を登録する

Spring 実行器は @XxlJob 付きメソッドを見つけ、対象オブジェクトとメソッドを登録します。これは発見後の登録ループです。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/handler-registration.java.txt" -->
```java
            // 2.3、scan + registry Jobhandler
            Object jobBean = applicationContext.getBean(beanName);
            for (Map.Entry<Method, XxlJob> jobMethodEntry : annotatedMethods.entrySet()) {
                Method jobMethod = jobMethodEntry.getKey();
                XxlJob xxlJob = jobMethodEntry.getValue();
                // regist
                registryJobHandler(xxlJob, jobBean, jobMethod);
            }
```

ハンドラー登録と実行器アドレス登録は別です。オンラインでも handler が見つからないなら、実際に配置された名称を確認します。Cron の変更を繰り返しても直りません。

## 第四段階：業務メソッドを呼ぶ

BEAN モードは登録済みオブジェクトのメソッドをリフレクションで呼びます。引数なしなら直接呼び、引数ありなら同じ長さの配列を渡します。ジョブの JSON を自動的に業務 DTO へ変換する意味ではなく、本プロジェクトは自分で読み取りと解析を行います。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/handler-invocation.java.txt" -->
```java
    @Override
    public void execute() throws Exception {
        Class<?>[] paramTypes = method.getParameterTypes();
        if (paramTypes.length > 0) {
            method.invoke(target, new Object[paramTypes.length]);       // method-param can not be primitive-types
        } else {
            method.invoke(target);
        }
    }
```

実行スレッドは待機制御、タイムアウト、結果も管理します。タイムアウト時の割り込みは、外部通信やコミット済み DB 操作の取り消しを保証しません。復旧前に業務の副作用を調べます。

## 第五段階：完了結果を返す

結果はコールバックのキューに入り、別スレッドがまとめてセンターへ返します。以下は取り出しと集約です。コールバック失敗時の再試行は後続の処理が担います。

<!-- omni:source path="docs/reference/upstream/xxl-job-3.3.1/completion-callback.java.txt" -->
```java
                        CallbackRequest callback = getInstance().callBackQueue.take();
                        if (callback != null) {

                            // collect callback data
                            List<CallbackRequest> callbackParamList = new ArrayList<>();
                            callbackParamList.add(callback);                                            // add one element
                            int drainToNum = getInstance().callBackQueue.drainTo(callbackParamList);    // drainTo other all elements

                            // do callback, will retry if error
                            if (CollectionTool.isNotEmpty(callbackParamList)) {
                                doCallback(callbackParamList);
                            }
```

画面に割り当て結果と実行結果が別々にある理由です。割り当て成功でも完了待ちなら、業務成功とも失敗とも断定せず、実行ログと結果の返送を確認します。

## 出典と次のページ

公式コミット 7548d1a62b187684b698e05618947f26d3e4a54a の原文で、著作権者は xuxueli です。パス、行、ハッシュは[出典記録](../reference/upstream/xxl-job-3.3.1/provenance.json)にあります。この版の完全な [GPL v3 ライセンス](../reference/upstream/xxl-job-3.3.1/LICENSE.txt)と未変更の [NOTICE](../reference/upstream/xxl-job-3.3.1/NOTICE.txt)も同梱します。続けて[プロジェクトへの接続](distributed-jobs-project.jp.md)を読めます。
