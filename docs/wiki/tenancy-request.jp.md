# 4. プロジェクトでの実践：信頼できるテナントをリクエストに渡す

李さんの検索を DB まで追います。会社を選んでログインし、ゲートウェイが資格情報を確認し、サービスが今回の身元を作り、対応する規則でデータを取得する流れです。

## 第一段階：正しい会社でアカウントを探す

会社選択は表示名を変えるだけではありません。Auth は対象テナント内の有効な利用者を探します。SysUserMapper の連続原文です。

<!-- omni:source path="omni-backend/omni-auth/src/main/java/com/omni/auth/mapper/SysUserMapper.java" -->
```java
    @Select("SELECT * FROM sys_user WHERE tenant_id = #{tenantId} AND username = #{username} AND status = 1")
    SysUser selectByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);
```

tenant_id、username、status=1 を同時に使い、別会社の同名アカウントを区別します。成功後に発行する JWT にも tenant_id を含めます。

ブラウザーでテナントヘッダーを送れても、それで会社を切り替える権限は得られません。クライアントの値はまだ信頼できない入力です。

## 第二段階：検証した資格情報から身元を書き直す

保護された要求で Gateway は JWT の署名、期限、利用者・テナントのクレーム、トークンのブラックリストを確認します。成功後の書換え部分です。

<!-- omni:source path="omni-backend/omni-gateway/src/main/java/com/omni/gateway/filter/AuthFilter.java" -->
```java
                    ServerHttpRequest mutatedRequest = request.mutate()
                            .headers(h -> {
                                h.remove(AUTH_HEADER);
                                TRUSTED_IDENTITY_HEADERS.forEach(h::remove);
                                h.set(GATEWAY_FORWARDED_HEADER, GATEWAY_FORWARDED_VALUE);
                                // 向后兼容：旧服务仍可读明文身份头；新服务必须验证 X-Gateway-Assertion
                                h.set("X-User-Id", claims.getSubject());
                                h.set("X-Tenant-Id", getClaimAsString(claims, "tenant_id"));
                                h.set("X-User-Name", getClaimAsString(claims, "username"));
                                h.set("X-User-Roles", getClaimAsString(claims, "roles"));
                                h.set("X-User-Scopes", getClaimAsString(claims, "scope"));
                                if (assertionHeader != null) {
                                    h.set(GATEWAY_ASSERTION_HEADER, assertionHeader);
                                }
                            })
                            .build();
```

受信 Bearer と信頼対象の身元ヘッダーを消し、検証済みクレームから利用者、会社、ロール、権限を埋め、経路のゲートウェイアサーションを渡します。クライアントの X-Tenant-Id が資格情報の会社を上書きすることはありません。

アサーションは身元の出所を下流が確認する署名材料です。現在は userId、tenantId、username、発行時刻を署名し、ロール・権限ヘッダー、メソッド、アドレス、本文は含みません。再送防止用のランダム識別子 nonce もありません。ロールと権限はゲートウェイの除去・再設定と信頼するサービスネットワークにも依存し、要求全体への署名ではありません。

## 第三段階：出所を検証してから今回の身元を保持する

common-service の通常業務チェーンでは、GatewayPreAuthenticationFilter がアサーション、時刻、平文ヘッダーとの一致を確認して認証を作ります。その後 ServiceIdentityFilter が利用者・テナント ID の正整数を確認し、ServiceIdentityContext へ保持します。

前者は出所の検証、後者はサービスコードが参照できる場所への保存です。後者だけを呼んでも署名検証にはなりません。

終了時の片付けも必要です。ServiceIdentityFilter の末尾です。

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/identity/ServiceIdentityFilter.java" -->
```java
            ServiceIdentityContext.set(new ServiceRequestIdentity(
                    userId, tenantId, request.getHeader("X-User-Name")));
            filterChain.doFilter(request, response);
        } finally {
            ServiceIdentityContext.clear();
        }
```

下流で例外が起きても finally で消します。サーバーは作業スレッドを再利用するため、前の身元を次の要求へ残してはいけません。

公開画面、管理端点、内部呼出しには別のパスポリシーがあります。すべての HTTP がこの通常業務チェーンを使うわけではありません。

## 第四段階：対象表へ会社条件を付ける

common-service はテナント能力が有効なら次を登録します。会社は信頼できる身元から、対象表はドメインの規則から取得します。

<!-- omni:source path="omni-backend/omni-common-service/src/main/java/com/omni/common/service/config/ServicePersistenceAutoConfiguration.java" -->
```java
        if (properties.getTenant().isEnabled()) {
            TenantTablePolicy tenantPolicy = requireUnique(tenantPolicyProvider, "TenantTablePolicy");
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    return new LongValue(ServiceIdentityContext.requireTenantId());
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    return !tenantPolicy.appliesTo(tableName);
                }
            }));
        }
```

requireTenantId は有効な身元がなければ失敗し、既定の会社を想像しません。appliesTo は「このサービスのテナント業務表か」を答えます。

完全なリポジトリでは CRM、SRM、購買、資産が crm_、srm_、proc_、ast_ を対象にします。sys_mq_message を除外するのは、中継が他社の未送信メッセージも走査するためです。全表に tenant_id があるとは限りません。

## 同じプロジェクトにも別の経路がある

Flowable は承認を進めるエンジンです。業務拡張表はプロジェクトの補足情報、エンジン表はフレームワークの実行情報を持ち、帰属を別に扱います。JDBC は DB 接続を直接使う方法で、これらの MyBatis プラグインを自動では通りません。

| 経路 | 現在の処理 | 推測できないこと |
|---|---|---|
| Auth のアカウント等 | Mapper/Service がテナント条件を明示し、独自の範囲処理と併用 | 汎用 TenantLine はなく、ドメイン設定では証明できない |
| common-service のドメイン | 有効化、身元、表ポリシー、範囲処理を組合せ | 直接 JDBC や未登録表への自動保護 |
| Workflow の拡張とエンジン | wf_ と ACT_* を別処理し、独自のコンテキストとクエリー条件 | エンジン表をドメインプラグインだけに任せられない |
| 内部・バックグラウンド | 対応する認証と明示的なテナント引数 | ブラウザースレッドからの自動継承や全経路の公開ゲートウェイ通過 |

<!-- omni:modules:workflow -->
### フローサービスにある現在の制約

Workflow の MybatisPlusConfig は TenantInfoHolder を読み、次の既定分岐を持ちます。

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/config/MybatisPlusConfig.java" -->
```java
            @Override
            public Expression getTenantId() {
                // 从 SecurityContext 或请求头中获取租户 ID，默认返回 1
                String tenantId = com.omni.common.workflow.tenant.TenantInfoHolder.getTenantId();
                return new LongValue(tenantId != null ? Long.parseLong(tenantId) : 1L);
            }
```

コンテキスト不足で 1 に戻るのは現在の実装で、一般的な推奨ではありません。「会社がなければ最初の会社にする」と模倣したり、common-service の拒否がここにも適用済みと記載したりしないでください。

将来の厳格化には、初期化、内部呼出し、バックグラウンド、エンジンの配置を別に検証する必要があります。説明を統一するために実装を変更することはしません。
<!-- omni:modules:end -->

## 更新にはさらに条件がある

会社を限定しても、更新の操作権限、記録の帰属、状態、版、影響行数の確認が残ります。一覧で見えないことと変更できないことは別の責任です。

次は[ロール・組織・データ範囲](tenancy-permissions.jp.md)で、会社の内部の分担を見ます。
