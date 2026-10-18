# ソース調査：症状から境界へ

基準：主幹 fc165e3 と今回の文書ブランチ。現動作はリンク先ソースで確認します。まず [ソース索引](../reference/source-map.jp.md)を読み、独立した学習環境で実行します。共有業務データに障害を注入しません。

<!-- omni:modules:auth -->
## captcha-jwt

**問題と呼出し経路**：誤った CAPTCHA の後、再送信も失敗するのはなぜか。

ログイン要求はまず validate を呼びます。Redis GETDEL は比較結果に関係なく値を原子的に消費します。generateToken では tenantId、roles、permissions、jti、有効期限を確認します。答え、パスワード、token 全体は記録しません。

- [CaptchaServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java) — `validate`
- [JwtTokenServiceImpl.java](../../omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/JwtTokenServiceImpl.java) — `generateToken`

**停止点、失敗と期待結果**：getAndDelete の前後に停止します。stored は最初だけ存在し、次は空です。失敗後は画像を更新します。実画面の CAPTCHA は人が入力し、回避方法は設けません。

**関連テスト**：この単元には独立した CAPTCHA 単体テストがありません。実ログインと一回限りの消費にはコンテナの統合証拠が必要で、合格とは記録できません。

要求は captchaKey/captchaCode を伴う POST /api/auth/login です。redisKey は一回限りの記録を指し、stored=null は期限切れ・不存在・消費済みを意味します。パスワード確認前に消費されます。以下は実際の validate 抜粋で、停止位置を示します。stored の回答値は記録しません。

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/service/impl/CaptchaServiceImpl.java" -->
```java
        String stored = stringRedisTemplate.opsForValue().getAndDelete(redisKey);

        if (stored == null) {
            // 键不存在，说明验证码已过期、不存在或已被并发消费
            throw new BusinessException(400, "验证码已过期");
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:gateway -->
## gateway-assertion

**問題と呼出し経路**：X-User-Id を偽造しても管理者になれない理由。

AuthFilter は外部の識別ヘッダーを削除し、検証済み JWT から識別情報と HMAC 証明を作ります。後段でも署名と時刻を検証します。鍵がなければ 503 になります。

- [AuthFilter.java](../../omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java) — `filter`

**停止点、失敗と期待結果**：filter と後段の検証に停止し、ヘッダー名、検証結果、状態コードを確認します。識別情報や証明の改変はテストで拒否されます。鍵は表示しません。

**関連テスト**：[AuthFilterHmacTest.java](../../omni-backend/omni-gateway/src/test/java/com/omni/gateway/filter/AuthFilterHmacTest.java) · [GatewayPreAuthFilterHmacTest.java](../../omni-backend/omni-base/src/test/java/com/omni/base/security/GatewayPreAuthFilterHmacTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## data-scope

**問題と呼出し経路**：メニューは見えるが他部署の行が読めない場合。

機能権限は呼出しの可否、DataScope は対象行を決めます。bindScope はメソッド中だけ正規の範囲を結び、finally で解除します。SQL 順序は TenantLine → DataPermission → OptimisticLocker → Pagination です。

- [ServiceDataScopeAspect.java](../../omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java) — `bindScope`

**停止点、失敗と期待結果**：テナント、範囲種別、許可された組織/ユーザー ID、最終 WHERE を確認します。個人情報は出力しません。SELF は他人の行を読書きできず、例外後もコンテキストが空になります。子行は集約ルートから範囲を継承します。

**関連テスト**：[ServiceDataScopeAspectTest.java](../../omni-backend/omni-common-service/src/test/java/com/omni/common/service/datascope/ServiceDataScopeAspectTest.java) · [DataScopeResolveFilterTest.java](../../omni-backend/omni-auth/src/test/java/com/omni/auth/security/DataScopeResolveFilterTest.java)

identity は検証済み Gateway アサーションから得ます。scope は userId・tenantId・現在の permissionCode と一致しなければなりません。joinPoint.proceed は注釈対象の業務メソッドを呼び、成功・例外のどちらでも finally が ThreadLocal を消去します。範囲種別と許可 ID を確認し、個人情報は記録しません。

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/datascope/ServiceDataScopeAspect.java" -->
```java
        try {
            InternalDataScopeDTO scope = dataScopeResolver.resolve(identity, annotation.permissionCode());
            validate(scope, identity, annotation.permissionCode());
            ServiceDataScopeContext.set(scope);
            return joinPoint.proceed();
        } finally {
            ServiceDataScopeContext.clear();
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:workflow -->
## workflow-publish

**問題と呼出し経路**：同時公開と会署の早期終了を調べる方法。

publishModel は SELECT FOR UPDATE でモデルをロックし、BPMN 検証後に配備と版更新を行います。completionCondition で省略されたタスクは HistoricTaskInstance.deleteReason=MI_END で判定し、活動の親から推測しません。

- [WorkflowModelServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowModelServiceImpl.java) — `publishModel`
- [ProcessInstanceServiceImpl.java](../../omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java) — `MI_END`

**停止点、失敗と期待結果**：公開ロック、validate 呼出し、配備直前に停止して modelKey、tenantId、版を確認します。不正 BPMN は配備されません。タスクの deleteReason と実際の承認記録を確認します。MI_END はエンジンによるタスクのスキップで、履歴には auto-approved と表示されます。人が投じた賛成票や否決ではありません。

**関連テスト**：[WorkflowModelServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowModelServiceImplTest.java) · [WorkflowApprovalServiceImplTest.java](../../omni-backend/omni-workflow/src/test/java/com/omni/workflow/service/impl/WorkflowApprovalServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

**問題と呼出し経路**：保存済みメッセージの再試行が待機する理由。

業務トランザクションが PENDING を保存します。relayPendingMessages は期限に達した行を走査し、claimForRelay の CAS で PROCESSING リースを取得します。送信後の状態更新も CAS で保護します。失敗は newRetryCount を増やし、2^newRetryCount × 10s 待機して上限で DEAD_LETTER へ移ります。未知 broker も失敗です。

- [MqMessageRelayService.java](../../omni-backend/omni-common-mqlog/src/main/java/com/omni/common/mqlog/relay/MqMessageRelayService.java) — `relayPendingMessages`

**停止点、失敗と期待結果**：id、status、retryCount、nextRetryTime、取得行数を確認します。二つの worker のうち一つだけが取得します。背景処理はテナント横断ですが外部照会は tenantId 必須です。障害注入は隔離コンテナ限定で、復旧後に再送と消費側の冪等性を確認します。

**関連テスト**：[MqMessageRelayServiceTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceTest.java) · [MqMessageRelayServiceCasTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceCasTest.java) · [MqMessageRelayServiceUnknownBrokerTest.java](../../omni-backend/omni-common-mqlog/src/test/java/com/omni/common/mqlog/relay/MqMessageRelayServiceUnknownBrokerTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## portal-saga

**問題と呼出し経路**：入居登録直後に取引先情報が見えない理由。

enroll は inviteToken と requestId を検証し、ローカルトランザクションで関連を保存して役割付与イベントを出します。Auth が SUPPLIER を付与して結果を返し、SRM が関連を完成します。USER は登録のみ可能で、情報/評価には役割と有効な関連が必要です。

- [SupplierPortalServiceImpl.java](../../omni-backend/omni-srm/src/main/java/com/omni/srm/service/impl/SupplierPortalServiceImpl.java) — `enroll`

**停止点、失敗と期待結果**：enroll と結果適用に停止し requestId、関連状態、相関キーを確認します。失敗は retryEnrollment で復旧し、重複結果で役割を重複付与しません。ポータルのユーザー ID を内部 owner_user_id/owner_unit_id にしません。

**関連テスト**：[SupplierPortalServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/SupplierPortalServiceImplTest.java) · [PortalRoleResultServiceImplTest.java](../../omni-backend/omni-srm/src/test/java/com/omni/srm/service/impl/PortalRoleResultServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## receipt-inbox

**問題と呼出し経路**：受入イベントの再送や補完で資産が二重作成されない理由。

importEvent は版とテナントを検証します。qualityStatus=PASS、assetManaged=true、正整数 assetQuantity の行だけをカード化します。Inbox eventId と受入行/単位連番が二つの冪等性境界です。金額 JSON は十進文字列です。

- [ProcurementAssetImportServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImpl.java) — `importEvent`

**停止点、失敗と期待結果**：eventId、tenantId、sourceGrLineId、単位連番、ImportResult を確認します。処理済みまたは無視済みの同一イベントを再送すると `replayed=true`、`createdCount=0` が返り、別イベントで同一出所を補完してもカード数は増えません。テナント衝突や小数数量は拒否します。テストは並行補完も扱います。

**関連テスト**：[ProcurementAssetImportServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/ProcurementAssetImportServiceImplTest.java)

<!-- omni:modules:end -->

<!-- omni:modules:asset -->
## asset-occupancy

**問題と呼出し経路**：同じ資産を同時に移管と廃棄できない理由。

prepareTransfer/prepareDisposal は一つのトランザクションで active_operation_type/active_operation_id を占有し previous_asset_status を保存します。完了は現操作と照合し、否決/取消で状態を復元して占有を解放します。承認は Workflow で実行します。

- [AssetOperationWorkflowStateServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImpl.java) — `prepareTransfer`
- [WorkflowCompletionServiceImpl.java](../../omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java) — `restoreAsset`

**停止点、失敗と期待結果**：assetId、version、占有、previousAssetStatus を確認します。二つ目は競合します。否決/取消後は両占有欄が空で状態が復元され、古い完了イベントは新操作を解放しません。

**関連テスト**：[AssetOperationWorkflowStateServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/AssetOperationWorkflowStateServiceImplTest.java) · [WorkflowCompletionServiceImplTest.java](../../omni-backend/omni-asset/src/test/java/com/omni/asset/service/impl/WorkflowCompletionServiceImplTest.java)

restoreAsset はテナント・資産 ID・版・現在状態・現在の占有を比較し、一つの UPDATE で状態復元と占有解除を行います。affected は 1 でなければ再試行可能例外となり、古い完了イベントが新操作を解除するのを防ぎます。previousAssetStatus は保存した業務値であり、このクラスのメソッド名ではありません。

<!-- omni:source path="omni-backend/omni-asset/src/main/java/com/omni/asset/service/impl/WorkflowCompletionServiceImpl.java" -->
```java
        int affected = assetMapper.update(null, new LambdaUpdateWrapper<AstAsset>()
                .eq(AstAsset::getTenantId, asset.getTenantId())
                .eq(AstAsset::getId, asset.getId())
                .eq(AstAsset::getVersion, asset.getVersion())
                .eq(AstAsset::getStatus, intent.currentStatus())
                .eq(AstAsset::getActiveOperationType, intent.operationType())
                .eq(AstAsset::getActiveOperationId, intent.operationId())
                .eq(AstAsset::getDeleted, 0)
                .set(AstAsset::getStatus, intent.previousStatus())
                .set(AstAsset::getActiveOperationType, null)
                .set(AstAsset::getActiveOperationId, null)
                .set(AstAsset::getUpdateTime, LocalDateTime.now())
                .set(AstAsset::getUpdateBy, "workflow-event")
                .setSql("version = version + 1"));
        if (affected != 1) {
            throw new RetryableWorkflowEventException("恢复资产 Workflow 操作占位失败");
        }
```

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## cli-atomic

**問題と呼出し経路**：サービスフォルダーの複製や Maven モジュール一つの削除だけでは不十分な理由。

applyRenderedIntegration は計画を事前検証し、一時ファイルとバックアップで復元可能な置換を行います。applyPresetGeneration は staging で catalog の依存関係を保ちながら不要部分を削除し、lock 検証後に rename します。Gateway、Compose、マイグレーション、シード、フロントエンド、文書も成果物に含めて整合を保ちます。

- [service-integration-transaction.ts](../../tools/omni-cli/src/service-integration-transaction.ts) — `applyRenderedIntegration`
- [preset-generator.ts](../../tools/omni-cli/src/preset-generator.ts) — `applyPresetGeneration`

**停止点、失敗と期待結果**：changes、選択モジュール、staging、出力先を確認します。隔離コピーのパス競合は書込み前に拒否されます。失敗は元の対象を保護し自分の staging のみ清理します。移動後の Wiki、AI、lock が元 checkout なしで成立することを確認します。

**関連テスト**：[service-integration-renderer.test.ts](../../tools/omni-cli/test/service-integration-renderer.test.ts) · [preset-generator.test.ts](../../tools/omni-cli/test/preset-generator.test.ts)

全変更を準備してから rename と書込み後検証を行います。以下の内側の catch は対象ファイルのバックアップ復元を試みて再スローし、外側の catch が rollback を呼び、完全復元と不完全な復元を区別します。現在のテストは 39 件の変更を扱い、38 件目の書込み後に failAfter で失敗させ、新規ファイルの削除、元ファイルの復元、トランザクションの一時ファイルの清掃を確認します。failAfter はテスト専用で、公開 CLI オプションではありません。

<!-- omni:source path="tools/omni-cli/src/service-integration-transaction.ts" -->
```typescript
  } catch (error) {
        if (item.backup && existsSync(item.backup) && !existsSync(item.target)) {
          renameSync(item.backup, item.target);
        }
        throw error;
      }
      item.applied = true;
      appliedCount += 1;
      if (testOptions.failAfter !== undefined && appliedCount === testOptions.failAfter) {
        throw new Error(`测试注入失败：第 ${appliedCount} 个文件`);
      }
    }

    for (const item of prepared) {
      const actual = readFileSync(item.target, 'utf8');
      if (actual !== item.change.after) throw new Error(`磁盘写后内容不一致：${item.change.target}`);
    }
  } catch (error) {
    const rollbackErrors = rollback(prepared, createdDirectories);
    const detail = error instanceof Error ? error.message : String(error);
    if (rollbackErrors.length > 0) {
      throw new CliError(`接入事务失败且回滚不完整：${detail}；${rollbackErrors.join('; ')}`);
    }
    throw new CliError(`接入事务失败，已完整回滚：${detail}`);
  }
```

<!-- omni:modules:end -->

## コンテナでのテストと調査

コマンドの前にツールの依存を導入します。backend runner は JDK 25/JAVA_HOME を提供し、全体 clean install がバックエンド変更の門です。IDE はコンテナ JDWP のクライアントとして接続できます。5005 はローカル限定です。停止点はスレッドを止めるため学習環境だけで使います。調査画像は実コンテナプロセスを示す必要があり、ソース画像は実行証拠にはなりません。
コマンドの前にツールの依存を導入します。backend runner は JDK 25/JAVA_HOME を提供し、全体 clean install がバックエンド変更時の検証です。IDE はコンテナ JDWP のクライアントとして接続でき、ホストの 5005 はローカルにのみバインドします。停止点はスレッドを止めるため学習環境だけで使います。下記の対象テストは Surefire の引数で調査設定を渡します。Maven とテスト JVM が 5005 を取り合わないよう、グローバルな JAVA_TOOL_OPTIONS を併用しません。調査画像は実コンテナプロセスを示す必要があり、ソース画像は実行証拠にはなりません。
```bash
docker compose -p omni-stack-tools -f compose.tools.yaml build cli
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm ci
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm cli npm test
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw clean install
# JDWP: only in an isolated teaching environment
# Set JAVA_TOOL_OPTIONS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005
# Then connect the IDE to localhost:5005 using the runner's declared port.
```

断言は動作を説明しますが、ソースリンクは実行を証明しません。コマンド、イメージ ID、ソース摘要、終了コード、断言結果を記録し、token、cookie、個人データを除きます。

## Docker 内で一つのテストをデバッグする

業務データへ故障を注入せず、ソース単位と対応テストを選びます。以下は Outbox CAS テストを選択するコマンドで、Reactor 全体や E2E 業務の合格を意味しません。

```bash
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm backend ./mvnw install -pl omni-common-mqlog -am -DskipTests
docker compose -p omni-stack-tools -f compose.tools.yaml run --rm --service-ports backend ./mvnw test -pl omni-common-mqlog -am -Dtest=MqMessageRelayServiceCasTest -Dsurefire.failIfNoSpecifiedTests=false "-Dmaven.surefire.debug=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
```

二つ目は Docker 内のテスト JVM を停止状態で待機させ、コンテナのインターフェースで接続を受け付けます。compose.tools.yaml がホスト側で公開するのは引き続き 127.0.0.1:5005 のみです。IDE をこの固定アドレスに接続し、チェックアウトをソースへ対応付けます。claimForRelay、casMarkSent/casApplyFailure で停止し、取得回数・状態・更新行数を確認します。競合する二者のうち取得成功は一者だけで、古い更新の行数 0 を成功扱いしません。この単体テストは Mock と AtomicBoolean で取得を模擬し、別途 SQL 注釈を検査します。実データベースや Broker を通じた配信の証明ではありません。再開後に対象テストの結果を確認します。上流に同名テストがないことと対象テストの skip は別です。自分のデバッグコンテナを終了し、業務ポートは変更しません。

5005 が使用中なら所有者と自分のデバッグコンテナを確認し、自分のプロセスだけを停止して再試行します。自動ポート変更は行いません。docs/scripts/source-excerpts.test.mjs は抜粋と現在ソースの一致を検証します。ソース変更時は四言語の解説を同期し、実際の AI_REVIEW を行います。人による文書レビューは必須ではありません。停止点や変数の画像は実接続と確認後に登録し、静的なコード画像をデバッグ証拠にしません。

コマンドの根拠：[Surefire のデバッグ説明](https://maven.apache.org/surefire/maven-surefire-plugin/examples/debugging.html)、[JDK 25 JDWP のアドレス仕様](https://docs.oracle.com/en/java/javase/25/docs/specs/jpda/conninv.html)。[今回のソース確認記録](../reference/evidence/source-debugging-wiki-review-2026-10-17.json)に実際の読取り範囲を残しています。各テストの証明範囲はその実際の検証項目に限られ、今回の文書レビューではテスト、IDE 接続、業務書込みを新たに実行していません。
