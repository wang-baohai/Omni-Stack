# 5. プロジェクトでの実装：起案者、重複防止、次の承認者

購入申請が業務上の検査を通っても、誰の申請か、どの規則を使うか、「部門長」が誰かを決める必要があります。

## 二つの開始入口を分ける

| 入口 | 呼び出し元 | 本人確認と重複要求 |
|---|---|---|
| 通常開始 | ゲートウェイ経由の process-instance/start | 現在の本人と workflow:instance:start を確認。内部開始の予約による重複防止は自動適用されない |
| 内部開始 | 業務サービスから internal/workflow/process-instance/start | 共有トークン、テナント整合性を確認。呼び出し側が信頼できる起案者を渡し、要求・業務キーを予約する |

内部 API は、番号を知っている人になり代わって開始できる仕組みではありません。共有トークンはサービス呼び出しを保護しますが、業務サービス側で利用者の権限も確認します。startUserId/startUserName を利用者の自由入力にはしません。

通常開始は他人への偽装となるシミュレーションを拒否し、受け取った変数から起案者の予約キーを除きます。ProcessInstanceServiceImpl の原文です。

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java" -->
```java
        safeVariables.remove("initiator");
        safeVariables.remove("startUserId");
        safeVariables.remove("authenticatedUserId");
        safeVariables.remove("START_USER_ID");
        // 启动会同步执行首个节点，候选人解析必须在此时已能读取可信发起人。
        safeVariables.put("initiator", String.valueOf(effectiveUserId));
```

最初のノードが担当者を探す時点で、信頼できる起案者を読めます。Flowable の認証コンテキストは finally で消去し、同じスレッドの次の要求へ残さないようにします。これは開始時の処理であり、その後の承認変数すべてを除外検査したという意味ではありません。

## 名前だけでなく、確定した版を使う

modelVersionId を指定すると、同じテナントで公開済み、かつエンジン定義を持つ版を要求します。通常入口には processKey とテナントで開始する互換経路も残り、モデル一覧の現在の公開版と一致する保証はありません。

内部開始は公開状態を再確認します。承認経路の保存時に検証済みでも十分ではありません。資産移管、資産処分、仕入先の取引開始承認は、モデル分類と業務種別の完全一致も必要です。

## 冪等性：再送しても同じ申請と認識する

ここでいう冪等性は、同じ業務意図の再送で別のプロセスを作らないことです。内部予約はテナント内の requestId と businessType + businessKey をそれぞれ一意にします。挿入を試み、競合時は内容を判断します。

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowProcessStartRequestServiceImpl.java" -->
```java
        if (requestMapper.insertIgnore(candidate) == 1) {
            return new Reservation(candidate, true, true);
        }
        return resolveConflict(candidate);
    }
```

重複なら何でも成功と返すわけではありません。業務識別子、モデル版、起案者が同じかを比較し、不一致を拒否します。タイトル、表示名、変数全体のハッシュは比較しません。

STARTED なら元のインスタンスと replayed=true を返し、処理中なら 409 です。同じ requestId の FAILED は再予約できます。別の requestId に変えて同じ失敗済み業務キーに当たっても、自動で再試行権を得られません。タイムアウト時は正式な検索で状態を確認し、同じ意図・同じ requestId で復旧します。

## 「部門長」を実際の人に変える

役割は資格、組織は探す範囲、アンカーは今回どの組織から探すかを表します。BPMN の omni:assignment 拡張に規則を保存します。形の例は次のとおりです。

```json
{
  "assignmentType": "SCOPED_ROLE",
  "roleCode": "SUPER_ADMIN",
  "anchorType": "START_USER_PRIMARY_UNIT",
  "anchorParams": {},
  "scopeMode": "SAME_UNIT",
  "fallbackStrategy": "ERROR",
  "approvalMode": "ALL"
}
```

すべてを管理者承認にする推奨ではなく、アカウントや役割、組織を作る例でもありません。対象テナントで実際に設定してください。

| アンカー | 現在の起点 |
|---|---|
| START_USER_PRIMARY_UNIT | 起案者の有効な主組織 |
| PARENT_BY_TYPE、PARENT_CHILDREN | targetType に合う祖先。型がなければ主組織 |
| CHILD_BY_CODE、SIBLING_BY_CODE | コードで直属の子組織・同階層の組織を探す |
| DEPT_BY_CODE | テナント内の有効な組織をコードで検索 |
| ABSOLUTE_UNIT | 通常解析は unitIds または unitId を読む |
| PARENT | 主組織の親 |
| CHILD_UNIT、SIBLING_UNIT | 設定の unitId を直接使い、起案者の組織との上下・同階層の関係は自動検証しない |

SAME_UNIT は同組織、UNIT_AND_BELOW は当該組織と下位、CHILDREN_ONLY は直属の子です。未知の scopeMode は現在、同組織検索になります。候補者検索の組織範囲であり、データ権限の六種類の dataScope と同一ではありません。

## 人数の集合を読む前に選ぶ

設計画面はノード start の実行リスナーを登録し、**マルチインスタンスが集合を読む前**に候補者を解決します。ScopedRoleAssignmentListener の原文です。

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java" -->
```java
            // 3. 将候选人列表写入流程变量，供多实例 collection 使用
            delegateExecution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));

            candidateResolutionService.initApprovalCounters(
                    delegateExecution, approvalMode, candidateUserIds.size());
```

集合は candidateUserIds、各人の変数は userId、担当者も userId を参照します。票数は現在のマルチインスタンス根で初期化し、根に保存した印で再初期化や前ノードの票数混入を防ぎます。

旧 TaskListener はログのみで、この開始リスナーを置き換えられません。互換の集合式は解決サービスを共有しますが、旧代理処理の例外・代替処理は同じではありません。assignment や roleCode がないと処理を戻す場合もあり、すべての旧モデルが同じように失敗すると保証できません。

## 人が見つからなくても、自動承認にはしない

| 方針 | 現在の処理 |
|---|---|
| ERROR、既定、不明な方針 | 例外 |
| ASSIGN_ADMIN | 現テナントの組織 ID 1 から SUPER_ADMIN 候補を探す。ユーザー 1 の固定指定ではなく、不在なら失敗 |
| ESCALATE_PARENT | アンカーの親から最大 20 階層上がり、最初の候補を採用。見つからなければ失敗 |

上位探索の ABSOLUTE_UNIT は単独の unitId を読み、unitIds の先頭を自動採用しません。組織の欠落、無効ユーザー、役割・範囲の不一致、検索例外でも候補が空になります。正しい設定と正規のユーザー・組織データを補い、業務の再試行規則で復旧します。自動承認で回避しません。

規則を変えたら新版を公開します。実行中の全申請を自動変更する独立した実行時候補設定表はありません。次は[タスク操作と複数人承認](workflow-tasks.jp.md)です。
