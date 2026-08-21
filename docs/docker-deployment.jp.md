# Docker フルスタックデプロイメント完全ガイド

> 本ドキュメントは Omni-Stack Docker デプロイメントの完全な技術リファレンスです。アーキテクチャ設計、ビルド原理、設定詳細、運用操作、トラブルシューティングを網羅しています。  
> クイックスタートは [README.jp.md](../README.jp.md) の Docker ワンクリックデプロイメントセクションを参照してください。

---

## 目次

- [1. アーキテクチャ概要](#1-アーキテクチャ概要)
- [2. コンテナネットワークトポロジ](#2-コンテナネットワークトポロジ)
- [3. 起動チェーンとヘルスチェック](#3-起動チェーンとヘルスチェック)
- [4. マルチステージビルドの原理](#4-マルチステージビルドの原理)
- [5. docker-compose.yml サービス別設定解説](#5-docker-composeyml-サービス別設定解説)
- [6. 環境変数オーバーライドメカニズム](#6-環境変数オーバーライドメカニズム)
- [7. Nginx リバースプロキシ設定](#7-nginx-リバースプロキシ設定)
- [8. データ初期化と永続化](#8-データ初期化と永続化)
- [9. Docker レジストリミラー設定](#9-docker-レジストリミラー設定)
- [10. Windows Hyper-V/WSL2 ポート予約問題](#10-windows-hyper-vws12-ポート予約問題)
- [11. Nacos v3.1.1 ヘルスチェックエンドポイント変更](#11-nacos-v311-ヘルスチェックエンドポイント変更)
- [12. RocketMQ Broker Docker ネットワーク設定](#12-rocketmq-broker-docker-ネットワーク設定)
- [13. スケーリングガイド](#13-スケーリングガイド)
- [14. 運用操作マニュアル](#14-運用操作マニュアル)
- [15. トラブルシューティングガイド](#15-トラブルシューティングガイド)
- [16. 設定リファレンス](#16-設定リファレンス)

---

## 1. アーキテクチャ概要

Omni-Stack Docker フルスタックは **15 個のコンテナ**を含み、3 つのレイヤーに分類されます：

```
┌─────────────────────────────────────────────────────────────┐
│                  フロントエンド層（1 コンテナ）               │
│  omni-frontend (Vue 3 + Nginx:3000)                        │
└──────────────────────────┬──────────────────────────────────┘
                           │ proxy_pass
┌──────────────────────────┴──────────────────────────────────┐
│              バックエンドマイクロサービス層（8 コンテナ）      │
│  Gateway · Auth · Base · Workflow · CRM · SRM              │
│  Procurement · Asset（公開入口は Gateway のみ）             │
└────────┼────────────────────┬───────────────────────────────┘
         │                    │
┌────────┴────────────────────┴───────────────────────────────┐
│                  ミドルウェア層（6 コンテナ）                 │
│  MySQL (:3306) · Redis (:6379) · Nacos (:8080/:8848/:9848) │
│  RocketMQ NameServer (:19876) · Broker (:10909-10912)      │
│  XXL-JOB Admin (:18080)                                    │
└─────────────────────────────────────────────────────────────┘
```

**設計原則**：
- すべてのバックエンドマイクロサービスコンテナの内部ポートは **8080** に統一し、ホストポートマッピングで区別
- コンテナ間通信は **Docker 内部ネットワーク**（`omni-network`）を使用し、コンテナ名で解決
- フロントエンド Nginx は静的ファイルと API リバースプロキシの両方を提供 — ユーザーは 1 つのポート（3000）のみアクセス

---

## 2. コンテナネットワークトポロジ

すべてのコンテナは Docker ブリッジネットワーク `omni-network` を共有します：

```yaml
networks:
  omni-network:
    driver: bridge
```

**ネットワーク通信ルール**：

| 送信元コンテナ | 送信先コンテナ | 使用アドレス | 説明 |
|---------------|---------------|-------------|------|
| omni-frontend | omni-gateway | `omni-gateway:8080` | Nginx proxy_pass（内部ポート） |
| omni-gateway | omni-auth | `omni-auth:8080` | Spring Cloud Gateway ルーティング |
| omni-auth | mysql | `mysql:3306` | JDBC 接続 |
| omni-auth | redis | `redis:6379` | キャッシュ/セッション |
| omni-auth | nacos | `nacos:8848` | サービス登録/設定 |
| omni-base | rocketmq-namesrv | `rocketmq-namesrv:9876` | MQ メッセージ送信 |
| omni-base | xxl-job-admin | `xxl-job-admin:8080` | ジョブ実行登録 |
| ホストブラウザ | omni-frontend | `localhost:3000` | ユーザーアクセス入口 |
| ホストブラウザ | Nacos Console | `localhost:8080` | 運用管理 |

> **重要な区別**：コンテナ間通信は内部ポート（例：8080）を使用し、ホストからのアクセスはマッピングポート（例：8100/8101/8102/8103）を使用します。

---

## 3. 起動チェーンとヘルスチェック

### 3.1 レイヤー起動順序

コンテナは `depends_on` + `condition: service_healthy` を使用して 5 レイヤーに整理され、順序付き起動を確保します：

```
Layer 0:  mysql · redis · rocketmq-namesrv          （依存関係なし、最初に起動）
            │         │          │
Layer 1:  nacos     xxl-job   rocketmq-broker        （Layer 0 に依存）
            │         │          │
Layer 2:  omni-auth                                  （nacos + redis + mysql に依存）
            │
Layer 3:  omni-base · omni-workflow · omni-gateway   （Layer 1 + Layer 2 に依存）
                                                │
Layer 4:  omni-frontend                           （omni-gateway に依存）
```

### 3.2 ヘルスチェック設定一覧

| サービス | チェック方式 | interval | timeout | retries | start_period |
|---------|-------------|----------|---------|---------|--------------|
| MySQL | `mysqladmin ping` | 10s | 5s | 5 | 30s |
| Redis | `redis-cli ping` | 10s | 5s | 5 | 10s |
| Nacos | `curl http://localhost:8848/nacos/` | 15s | 5s | 5 | 60s |
| RocketMQ NameServer | TCP ポート探测 `/dev/tcp/127.0.0.1/9876` | 10s | 5s | 5 | 30s |
| RocketMQ Broker | TCP ポート探测 `/dev/tcp/127.0.0.1/10911` | 15s | 5s | 5 | 60s |
| XXL-JOB Admin | TCP ポート探测 `/dev/tcp/localhost/8080` | 15s | 5s | 5 | 60s |
| omni-auth | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-base | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-gateway | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |
| omni-workflow | `curl http://localhost:8080/actuator/health` | 15s | 5s | 5 | 90s |

> **なぜ start_period = 90s？**  
> Spring Boot マイクロサービスはコールドスタート時に多数の Bean のロード + データベース初期化 + Nacos 登録が必要で、60-80 秒かかる場合があります。90s に設定することで誤判定を防止します。

---

## 4. マルチステージビルドの原理

### 4.1 バックエンドマイクロサービスビルド

すべてのバックエンドマイクロサービスは共通の Dockerfile（`docker/backend/Dockerfile`）を共有し、`SERVICE_NAME` ビルド引数で区別します：

```dockerfile
# Stage 1: ビルド
FROM maven:3.9-eclipse-temurin-25-alpine AS build
COPY docker-settings.xml /root/.m2/settings.xml   # Aliyun Maven ミラー
WORKDIR /build

# POM ファイルを先にコピー（Docker レイヤーキャッシュを活用）
COPY mvnw pom.xml ./
COPY omni-common-core/pom.xml omni-common-core/
COPY omni-common/pom.xml omni-common/
# ... その他のモジュール POM ...
RUN mvn dependency:go-offline -B -q || true

# ソースコードをコピーして指定サービスをビルド
COPY . .
ARG SERVICE_NAME
RUN mvn package -pl ${SERVICE_NAME} -am -DskipTests -B -q

# Stage 2: ランタイム
FROM eclipse-temurin:25-jre-alpine
RUN apk add --no-cache curl bash
ARG SERVICE_NAME
COPY --from=build /build/${SERVICE_NAME}/target/*.jar /app/app.jar
ENV SERVER_PORT=8080
EXPOSE 8080
HEALTHCHECK ...
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

**レイヤーキャッシュ最適化戦略**：

1. **POM レイヤーキャッシュ**：すべての `pom.xml` をコピーして `dependency:go-offline` を実行 — POM が変わらない限り、後続ビルドはキャッシュレイヤーを再利用し、依存関係ダウンロードをスキップ
2. **ソース分離**：`COPY . .` はソースコード変更時のみリビルドをトリガー
3. **単一サービスビルド**：`mvn package -pl ${SERVICE_NAME} -am` はターゲットサービスとその依存関係のみビルドし、フルコンパイルを回避

**イメージ選定の考慮事項**：

| 選択 | 理由 |
|------|------|
| `maven:3.9-eclipse-temurin-25-alpine` | Alpine ベースは小さい（~200MB vs ~800MB for Debian）、Maven 3.9 + JDK 25 内蔵 |
| `eclipse-temurin:25-jre-alpine` | ランタイムには JRE のみ必要（~80MB）、フル JDK イメージより 300MB+ 小さい |

### 4.2 フロントエンドビルド

```dockerfile
# Stage 1: Node ビルド
FROM node:22-alpine AS build
WORKDIR /app
COPY omni-frontend/package*.json ./
RUN npm ci                                  # 正確インストール（lock ファイル使用）
COPY omni-frontend/ .
RUN npm run build                           # Vite プロダクションビルド

# Stage 2: Nginx ランタイム
FROM nginx:1.28-alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY docker/frontend/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
```

**レイヤーキャッシュ**：`package*.json` をコピーして `npm ci` を先に実行 — ソースコード変更時に依存関係の再インストールを回避。

---

## 5. docker-compose.yml サービス別設定解説

### 5.1 MySQL 8.4

```yaml
mysql:
  image: mysql:8.4
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:? .env に設定してください}
    MYSQL_DATABASE: omni_auth          # 自動作成される最初のデータベース
    MYSQL_CHARACTER_SET_SERVER: utf8mb4
    MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    TZ: Asia/Shanghai                  # タイムゾーン
  volumes:
    - omni-mysql-data:/var/lib/mysql
```

**ポイント**：
- MySQL entrypoint には集約 SQL をマウントせず、新規環境とアップグレード環境で同じ Liquibase チェーンを使用
- one-shot の `omni-db-migrator` が DB 作成、構造移行、正式シード、検証を行い、正常終了後にのみ Nacos、XXL-JOB、各アプリを起動
- アプリケーションは移行管理者ではなく、最小権限の `MYSQL_APP_USERNAME` / `MYSQL_APP_PASSWORD` を使用
- MySQL は名前付きボリューム `omni-mysql-data` を使用し、通常のコンテナ再作成ではデータを保持

### 5.2 Redis 7.4

```yaml
redis:
  image: redis:7.4
  command: ["redis-server", "--requirepass", "${REDIS_PASSWORD:? .env に設定してください}"]
```

### 5.3 Nacos v3.1.1

```yaml
nacos:
  image: nacos/nacos-server:v3.1.1
  environment:
    MODE: standalone                      # シングルノードモード
    SPRING_DATASOURCE_PLATFORM: mysql     # 外部 MySQL ストレージ（埋め込み Derby ではない）
    MYSQL_SERVICE_HOST: mysql             # MySQL コンテナ名を指定
    NACOS_AUTH_TOKEN: ...                 # JWT 署名シークレット（Base64 エンコード）
  ports:
    - "8080:8080"                         # コンソール
    - "8848:8848"                         # API ポート（サービス登録/設定）
    - "9848:9848"                         # gRPC ポート（ロングコネクション）
```

**3 ポート説明**：

| ポート | 用途 | 利用者 |
|-------|------|--------|
| 8080 | Web コンソール | 運用者ブラウザアクセス |
| 8848 | HTTP API | バックエンドマイクロサービス登録/設定 |
| 9848 | gRPC | Nacos 2.x+ クライアントロングコネクション |

### 5.4 RocketMQ 5.3.2（NameServer + Broker）

```yaml
rocketmq-namesrv:
  ports:
    - "19876:9876"     # ホスト 19876 → コンテナ 9876（Windows Hyper-V ポート競合回避）

rocketmq-broker:
  environment:
    NAMESRV_ADDR: rocketmq-namesrv:9876          # コンテナ間通信
    JAVA_OPT_EXT: "-Drocketmq.broker.diskSpaceWarningLevelRatio=0.98"
  volumes:
    - ./docker/rocketmq/broker-docker.conf:...   # Docker 専用 Broker 設定
  command: sh mqbroker -n rocketmq-namesrv:9876 -c ... --enable-proxy
```

> **ポートマッピング 19876:9876 の理由**：Windows Hyper-V/WSL2 は 9859-9958 のポート範囲を予約します。9876 を直接マッピングすると競合が発生します。[セクション 10](#10-windows-hyper-vws12-ポート予約問題)を参照。

### 5.5 XXL-JOB Admin v3.3.1

```yaml
xxl-job-admin:
  environment:
    PARAMS: >
      --spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?...
      --spring.datasource.username=root
      --spring.datasource.password=${MYSQL_ROOT_PASSWORD}
      --xxl.job.login.username=${XXL_JOB_ADMIN_USERNAME}
      --xxl.job.login.password=${XXL_JOB_ADMIN_PASSWORD}
      --xxl.job.accessToken=${XXL_JOB_ACCESS_TOKEN}
      --xxl.job.accessToken=           # 空トークン（開発環境では認証なし）
```

### 5.6 バックエンドマイクロサービス（4 インスタンス）

4 つのバックエンドマイクロサービスは同一の Dockerfile を使用し、`build.args.SERVICE_NAME` で区別します。各サービスの `environment` で主要設定をオーバーライド：

| 環境変数 | 説明 | 例値 |
|---------|------|------|
| `SERVER_PORT` | コンテナ内部ポート（統一 8080） | `8080` |
| `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | Nacos アドレス | `nacos:8848` |
| `SPRING_DATASOURCE_URL` | データベース接続 | `jdbc:mysql://mysql:3306/omni_auth?...` |
| `SPRING_DATA_REDIS_HOST` | Redis アドレス | `redis` |
| `SPRING_CLOUD_NACOS_DISCOVERY_IP` | 登録 IP（空=自動検出コンテナ IP） | `""` |
| `AUTH_ISSUER` | JWT Issuer（auth のみ） | `http://omni-auth:8080` |
| `AUTH_JWKS_URI` | JWKS エンドポイント（gateway のみ） | `http://omni-auth:8080/oauth2/jwks` |

### 5.7 フロントエンド

```yaml
omni-frontend:
  build:
    context: .                          # プロジェクトルート（docker/ 設定へのアクセス必要）
    dockerfile: docker/frontend/Dockerfile
  ports:
    - "3000:3000"                       # 唯一のユーザーアクセス入口
  depends_on:
    omni-gateway:
      condition: service_healthy        # ゲートウェイ準備完了後に起動
```

---

## 6. 環境変数オーバーライドメカニズム

Spring Boot は環境変数による `application.yml` 設定のオーバーライドをサポートしており、Docker デプロイメントで大量に使用されます。

### 6.1 変換ルール

| application.yml 設定 | 環境変数名 | 変換ルール |
|---------------------|-----------|-----------|
| `server.port` | `SERVER_PORT` | 大文字 + ドット→アンダースコア |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | 大文字 + ドット→アンダースコア |
| `spring.data.redis.host` | `SPRING_DATA_REDIS_HOST` | 大文字 + ドット→アンダースコア |
| `spring.cloud.nacos.discovery.server-addr` | `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` | 大文字 + ハイフン→アンダースコア |

### 6.2 優先順位（高→低）

```
1. コマンドライン引数（--server.port=9090）
2. 環境変数（SERVER_PORT=9090）
3. application-{profile}.yml
4. application.yml
5. Nacos 設定センター（有効な場合）
```

### 6.3 Docker デプロイメントでの典型的なオーバーライド

| 設定 | application.yml 値（ローカル開発） | Docker 環境変数値 |
|------|----------------------------------|-----------------|
| データベース URL | `localhost:3306` | `mysql:3306` |
| Redis ホスト | `localhost` | `redis` |
| Nacos アドレス | `localhost:8848` | `nacos:8848` |
| RocketMQ | `localhost:9876` | `rocketmq-namesrv:9876` |
| JWT Issuer | `http://localhost:8100` | `http://omni-auth:8080` |
| OAuth2 コールバック | `http://localhost:8102/api/auth/...` | `http://localhost:8102/api/auth/...` |

> **注意**：OAuth2 コールバック URI は Gateway の `localhost:8102` を使用します。Auth などの内部サービスポートは診断用にループバックへバインドし、外部公開しません。

---

## 7. Nginx リバースプロキシ設定

### 7.1 設定解説

```nginx
server {
    listen 3000;
    root /usr/share/nginx/html;

    # API リクエストを Gateway にリバースプロキシ
    location /api/ {
        proxy_pass http://omni-gateway:8080;    # ← コンテナ内部ポート！
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # OAuth2 エンドポイントリバースプロキシ
    location /oauth2/ {
        proxy_pass http://omni-gateway:8080;
        # ... 上記と同じ
    }

    # OIDC Discovery エンドポイントリバースプロキシ
    location /.well-known/ {
        proxy_pass http://omni-gateway:8080;
        # ... 上記と同じ
    }

    # Vue Router History モード
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

### 7.2 よくある間違い：proxy_pass ポート

| 設定 | 正しい？ | 説明 |
|------|---------|------|
| `proxy_pass http://omni-gateway:8080` | ✅ | コンテナ内部ポート、Docker ネットワーク内で到達可能 |
| `proxy_pass http://omni-gateway:8102` | ❌ | 8102 はホストマッピングポート、コンテナ間でアクセス不可 |
| `proxy_pass http://localhost:8102` | ❌ | コンテナ内の localhost は自分自身、Gateway に到達不可 |
| `proxy_pass http://host.docker.internal:8102` | ⚠️ | 動作するが Docker ネットワークを迂回、パフォーマンス劣化 |

---

## 8. データ初期化と永続化

### 8.1 データベース初期化チェーン

Compose は空ボリュームと後続アップグレードに同一の移行起動ゲートを適用します：

```
MySQL healthy
  → omni-db-migrator migrate（9 DB の Liquibase changeSet）
  → 8 seed source の SHA-256 と 24 件の自然キー検証
  → migrator 正常終了
  → Nacos / XXL-JOB / バックエンドサービス起動
```

構造の正規情報源は `database/changelog/` です。正式な DML シードは `scripts/sql/seed/` にあり、
`database/seed/manifest.yaml` で宣言します。`scripts/sql/init-all.sql` と旧 `migrate-*.sql` は互換性
クリーンアップまで残しますが、Compose 起動には使用しません。Flowable の実行時スキーマ更新は無効です。

### 8.2 データ永続化戦略

現在の設定では MySQL を名前付きボリューム `omni-mysql-data` に永続化します。`docker compose down` では保持され、`docker compose down -v` では復元不能で削除されます。

```yaml
mysql:
  volumes:
    - omni-mysql-data:/var/lib/mysql

volumes:
  omni-mysql-data:
```

---

## 9. Docker レジストリミラー設定

### 9.1 Docker レジストリミラー

Docker Hub へのアクセスが遅い地域のユーザーはミラーを設定してください：

**Linux**（`/etc/docker/daemon.json`）：
```json
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
```
```bash
sudo systemctl restart docker
```

**Windows / Mac**（Docker Desktop → Settings → Docker Engine）：
```json
{
  "registry-mirrors": ["https://docker.1ms.run"]
}
```

### 9.2 Maven 依存関係の高速化

バックエンド Dockerfile には Aliyun Maven ミラーが組み込まれています（`docker-settings.xml`）：

```xml
<mirror>
  <id>aliyun</id>
  <url>https://maven.aliyun.com/repository/public</url>
  <mirrorOf>central</mirrorOf>
</mirror>
```

### 9.3 npm 依存関係の高速化

フロントエンド npm インストールを高速化するには、Dockerfile にミラーレジストリを追加：

```dockerfile
RUN npm config set registry https://registry.npmmirror.com
RUN npm ci
```

---

## 10. Windows Hyper-V/WSL2 ポート予約問題

### 10.1 問題の説明

Windows 10/11 の Hyper-V または WSL2 は TCP ポート範囲を動的に予約します（例：9859-9958）。予約された範囲に Docker がマッピングするポート（例：9876）が含まれていると、コンテナ起動が失敗します：

```
Error starting userland proxy: listen tcp4 0.0.0.0:9876: bind: An attempt was made to access a socket in a way forbidden by its access permissions.
```

### 10.2 解決策

**解決策 A：ポートオフセット（採用済み）**

RocketMQ NameServer のホストポートを 9876 から 19876 に変更：

```yaml
ports:
  - "19876:9876"    # ホスト 19876 → コンテナ 9876
```

コンテナ間通信には影響なし（引き続き `rocketmq-namesrv:9876` を使用）。ホストからのアクセスのみ 19876 を使用。

**解決策 B：ポート予約保護（start.bat で実装済み）**

Docker 起動前に管理者権限で必要なポートを予約：

```batch
:: winnat 停止 → ポート予約 → winnat 再開
net stop winnat
netsh int ipv4 add excludedportrange protocol=tcp startport=9876 numberofports=1 persistent=yes
net start winnat
```

**解決策 C：WSL 再起動（一時的）**

```powershell
wsl --shutdown
# その後 Docker Desktop を再起動
```

### 10.3 予約ポートの確認

```powershell
netsh interface ipv4 show excludedportrange protocol=tcp
```

---

## 11. Nacos v3.1.1 ヘルスチェックエンドポイント変更

### 11.1 変更内容

Nacos v3.x から、`/nacos/actuator/health` エンドポイントが削除されました。ヘルスチェックは以下を使用：

| Nacos バージョン | ヘルスチェックエンドポイント | メソッド |
|----------------|--------------------------|---------|
| v2.x | `/nacos/actuator/health` | GET |
| v3.0+ | `/nacos/` | GET |

### 11.2 docker-compose.yml の設定

```yaml
healthcheck:
  test: ["CMD", "curl", "-sf", "http://localhost:8848/nacos/"]
  start_period: 60s     # Nacos は起動が遅く、40-60 秒必要
```

> **注意**：`curl -sf` — `-s` はサイレントモード、`-f` は HTTP エラー時に非ゼロ終了コードを返します。これらを組み合わせてヘルスチェックの余計な出力を防止します。

---

## 12. RocketMQ Broker Docker ネットワーク設定

### 12.1 背景

RocketMQ Broker はデフォルトで自身の IP を NameServer に登録します。Docker 環境では、Broker が `127.0.0.1` や Docker ブリッジ IP を取得し、他のコンテナから接続できない場合があります。

### 12.2 解決策

カスタム設定ファイル `docker/rocketmq/broker-docker.conf` で `brokerIP1` を明示的に設定：

```properties
brokerIP1 = rocketmq-broker    # コンテナ名に設定、Docker DNS で解決可能
```

### 12.3 バックエンド接続設定

バックエンドマイクロサービスは環境変数で RocketMQ に接続：

```yaml
SPRING_CLOUD_STREAM_ROCKETMQ_BINDER_NAME_SERVER: rocketmq-namesrv:9876
```

NameServer のコンテナ名と内部ポートを使用しており、ホストマッピングポート 19876 ではないことに注意してください。

---

## 13. スケーリングガイド

### 13.1 バックエンドサービスの水平スケーリング

```bash
# omni-base を 3 インスタンス起動
docker compose up -d --scale omni-base=3

# インスタンス状況を確認
docker compose ps
```

**注意事項**：
- 複数インスタンスの場合、Docker が自動的に異なるホストポートを割り当て
- Nacos サービスディスカバリがすべてのインスタンスを自動登録
- Spring Cloud Gateway が Nacos 経由で各インスタンスにロードバランシング
- 固定ポートが必要な場合は手動指定が必要

### 13.2 データベース接続プールの考慮事項

水平スケーリング時、各インスタンスは独立した接続プールを維持します。HikariCP `maximumPoolSize=20`、3 インスタンス = 60 データベース接続。MySQL の `max_connections` が十分であることを確認：

```sql
SHOW VARIABLES LIKE 'max_connections';
SET GLOBAL max_connections = 200;
```

### 13.3 ステートフルサービスの注意事項

- **XXL-JOB**：水平スケーリング非対応（シングル Admin モード）、スケジューリングデータは MySQL に保存
- **RocketMQ Broker**：本番環境ではマルチ Broker クラスタを推奨、現在はシングル Broker 開発モード
- **Nacos**：本番環境では 3 ノードクラスタを推奨、現在はスタンドアロンシングルノードモード

---

## 14. 運用操作マニュアル

### 14.1 常用コマンド

```bash
# すべてのコンテナステータスを表示
docker compose ps

# サービスログを表示（リアルタイム追跡）
docker compose logs -f omni-auth

# 単一サービスを再起動
docker compose restart omni-gateway

# サービスを再ビルドして再起動
docker compose up -d --build omni-base

# すべてのコンテナを停止（イメージは保持）
docker compose down

# 停止してすべてのデータを削除（完全リセット）
docker compose down -v

# コンテナリソース使用量を表示
docker stats --no-stream
```

### 14.2 ログ調査

```bash
# 最新 100 行のログを表示
docker compose logs --tail=100 omni-gateway

# 指定時間帯のログを表示
docker compose logs --since="2025-01-01T10:00:00" omni-auth

# ログをファイルに出力
docker compose logs omni-base > base-logs.txt
```

### 14.3 コンテナ内デバッグ

```bash
# バックエンドサービスコンテナに入る
docker exec -it omni-auth sh

# コンテナ内プロセスを表示
docker exec -it omni-auth ps aux

# コンテナ間ネットワーク接続性をテスト
docker exec -it omni-auth curl -s http://nacos:8848/nacos/
```

---

## 15. トラブルシューティングガイド

### 15.1 502 Bad Gateway

**症状**：ブラウザで `http://localhost:3000` にアクセスすると 502 が返される。

**診断手順**：
```bash
# 1. Nginx コンテナが稼働中か確認
docker compose ps omni-frontend

# 2. Gateway コンテナが稼働中か確認
docker compose ps omni-gateway

# 3. Nginx エラーログを確認
docker compose logs omni-frontend

# 4. コンテナ間接続性をテスト
docker exec -it omni-frontend curl -s http://omni-gateway:8080/actuator/health
```

**よくある原因**：
- Nginx `proxy_pass` がホストポート（8102）を使用している（コンテナ内部ポート 8080 の間違い）
- Gateway コンテナがまだヘルスチェックに合格していない
- Nacos が準備できる前に Gateway が起動した

### 15.2 イメージプル失敗

**症状**：`docker compose pull` がタイムアウトまたは `pull access denied` を報告。

**解決策**：
1. Docker レジストリミラーを設定（[セクション 9](#9-docker-レジストリミラー設定)を参照）
2. 手動プル確認：`docker pull xuxueli/xxl-job-admin:3.3.1`
3. ディスク容量確認：`docker system df`

### 15.3 ビルド失敗

**症状**：`docker compose build` が失敗。

**Maven 依存関係ダウンロードタイムアウト**：
- Aliyun ミラーが組み込み済み（`docker-settings.xml`）。まだ失敗する場合はネットワークを確認
- Docker ビルドキャッシュをクリア：`docker builder prune -a`

**npm install タイムアウト**：
- Dockerfile にミラーレジストリを追加（[セクション 9.3](#93-npm-依存関係の高速化)を参照）

**ディスク容量不足**：
```bash
docker system prune -a    # 未使用のイメージ/コンテナ/ネットワークをすべてクリーン
```

### 15.4 ポート競合

**症状**：`bind: address already in use`。

**診断**：
```bash
# Windows
netstat -ano | findstr :8080
# Linux/Mac
lsof -i :8080
```

**解決策**：
- ポートを占有しているプロセスを停止
- docker-compose.yml のホストポートマッピングを変更
- Windows ユーザー：Hyper-V ポート予約を確認（[セクション 10](#10-windows-hyper-vws12-ポート予約問題)を参照）

### 15.5 Nacos 登録失敗

**症状**：バックエンドログに `NacosException: failed to req API:/nacos/v1/ns/instance` が報告される。

**診断**：
```bash
# Nacos が正常か確認
docker compose ps nacos
docker exec -it omni-nacos curl -s http://localhost:8848/nacos/

# バックエンドサービスの Nacos 設定を確認
docker compose exec omni-auth env | grep NACOS
```

**よくある原因**：
- `SPRING_CLOUD_NACOS_DISCOVERY_SERVER_ADDR` が `nacos:8848` に設定されていない
- `SPRING_CLOUD_NACOS_DISCOVERY_IP` が空でない（自動検出のため `""` にすべき）
- バックエンドサービス起動時に Nacos がまだヘルスチェックに合格していなかった（depends_on 設定の問題）

### 15.6 RocketMQ 接続失敗

**症状**：バックエンドログに `org.apache.rocketmq.remoting.exception.RemotingConnectException` が報告される。

**診断**：
```bash
# Broker が NameServer に登録されているか確認
docker exec -it omni-rocketmq-namesrv sh mqadmin clusterList -n localhost:9876

# brokerIP1 設定を確認
docker exec -it omni-rocketmq-broker cat /home/rocketmq/rocketmq-5.3.2/conf/broker.conf
```

**よくある原因**：
- `brokerIP1` がコンテナ名 `rocketmq-broker` に設定されていない
- バックエンドサービスがコンテナポート 9876 ではなくホストポート 19876 を使用している

### 15.7 コンテナ起動順序異常

**症状**：バックエンドサービスの起動が失敗しているが `docker compose ps` ではコンテナが稼働中と表示される。

**診断**：
```bash
# サービス起動順序を表示
docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Health}}"

# サービス起動タイムスタンプを表示
docker compose logs --timestamps omni-auth | head -5
```

---

## 16. 設定リファレンス

### 16.1 サービスポートマッピング一覧表

| サービス | 内部ポート | ホストマッピングポート | プロトコル | 説明 |
|---------|-----------|---------------------|-----------|------|
| omni-frontend | 3000 | 3000 | HTTP | ユーザーアクセス入口 |
| omni-auth | 8080 | 8100 | HTTP | 認証サービス |
| omni-base | 8080 | 8101 | HTTP | 基礎データサービス |
| omni-gateway | 8080 | 8102 | HTTP | API ゲートウェイ |
| omni-workflow | 8080 | 8103 | HTTP | ワークフローサービス |
| omni-crm | 8080 | 8104 | HTTP | CRM サービス |
| omni-srm | 8080 | 8105 | HTTP | SRM サービス |
| omni-procurement | 8080 | 8106 | HTTP | 調達サービス |
| omni-asset | 8080 | 8107 | HTTP | 資産サービス |
| MySQL | 3306 | 13306 | TCP | データベース |
| Redis | 6379 | 6379 | TCP | キャッシュ |
| Nacos Console | 8080 | 8080 | HTTP | コンソール |
| Nacos API | 8848 | 8848 | HTTP | サービス登録/設定 |
| Nacos gRPC | 9848 | 9848 | gRPC | ロングコネクション |
| RocketMQ NameServer | 9876 | **19876** | TCP | ネームサービス |
| RocketMQ Broker | 10909-10912 | 10909-10912 | TCP | メッセージブローカー |
| XXL-JOB Admin | 8080 | 18080 | HTTP | ジョブスケジューラー |

### 16.2 認証情報と公開範囲

本番用の認証情報はハードコードされていません。起動前に `.env` で MySQL、Redis、Nacos、XXL-JOB、OAuth state、JWK 暗号化、内部 API、アプリケーション DB の各シークレットを設定してください。初期アプリケーションアカウントはローカルデモ専用であり、共有環境では変更または削除が必要です。公開入口はフロントエンド（`3000`）と Gateway（`8102`）のみで、その他のポートは `127.0.0.1` にバインドされます。

### 16.3 主要ファイルパス

| ファイル | パス | 説明 |
|---------|------|------|
| docker-compose.yml | `docker-compose.yml` | コンテナオーケストレーション設定 |
| バックエンド Dockerfile | `docker/backend/Dockerfile` | マイクロサービスマルチステージビルド |
| フロントエンド Dockerfile | `docker/frontend/Dockerfile` | Vue フロントエンドマルチステージビルド |
| Nginx 設定 | `docker/frontend/nginx.conf` | フロントエンドリバースプロキシルール |
| Broker 設定 | `docker/rocketmq/broker-docker.conf` | RocketMQ Docker ネットワーク設定 |
| Maven ミラー | `omni-backend/docker-settings.xml` | Aliyun Maven 高速化 |
| DB マイグレーション | `database/changelog/` | 9 DB の Liquibase スキーマ、制約、アップグレード |
| DB シード | `scripts/sql/seed/`、`database/seed/manifest.yaml` | 冪等 DML、ソースチェックサム、自然キー検証 |
| 起動スクリプト (Linux) | `start.sh` | ワンクリック起動 |
| 起動スクリプト (Windows) | `start.bat` | ワンクリック起動（ポート保護付き） |
| 停止スクリプト (Linux) | `stop.sh` | ワンクリック停止 |
| 停止スクリプト (Windows) | `stop.bat` | ワンクリック停止 |

---

## 付録：ビルド成果物サイズ参考

| イメージ | サイズ（約） | 説明 |
|---------|------------|------|
| omni-auth:latest | ~200MB | JRE + Fat JAR |
| omni-base:latest | ~200MB | JRE + Fat JAR |
| omni-gateway:latest | ~200MB | JRE + Fat JAR |
| omni-workflow:latest | ~250MB | JRE + Fat JAR + Flowable エンジン |
| omni-frontend:latest | ~50MB | Nginx + Vue 静的ファイル |
| mysql:8.4 | ~600MB | 公式イメージ |
| nacos/nacos-server:v3.1.1 | ~800MB | 公式イメージ |
| apache/rocketmq:5.3.2 | ~700MB | 公式イメージ |
