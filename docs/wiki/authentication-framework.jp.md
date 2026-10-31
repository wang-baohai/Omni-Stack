# 3. フレームワークとソース：プロトコルを実際の検査にする

前のページでは認可コードと PKCE を説明しました。ここでは内部を開き、誰がリクエストを受け、交換材料をどう比較し、同じコードを使い続けられない理由を見ます。

このプロジェクトの Spring Boot 4.0.6 が管理する Spring Security は 7.0.5 で、認可サーバーアーティファクトも 7.0.5 です。以下はその版の連続した原文です。まず前後の説明を読み、変数へ戻れば十分です。クラス名の暗記は不要です。

## プロトコルを全部コントローラーに書かない理由

ログインフォームを作ることと、認可プロトコルを維持することでは負担が異なります。後者にはクライアント登録、コールバック制限、コード、交換パラメーター、トークン形式、エラー処理も必要です。一つの条件が他アプリの接続方法にも影響します。

現在のソースから分かる選択肢は次のとおりです。

| 方法 | 引き受ける作業 | 現在のプロジェクトとの関係 |
|---|---|---|
| プロトコルの入口を自作 | 規則、安全性、互換性、テストの維持 | ローカル認可コード処理をこの方法で再実装してはいない |
| Spring 内で安全・認可サーバーのフレームワークを使う | 処理器を再利用し、クライアント・ログイン接続・鍵・業務権限を管理 | Spring Security と認可サーバーモジュールを使用 |
| 独立した身元管理基盤へ集約 | 基盤を運用し、業務アカウント・テナント・役割と連携 | 標準ローカル環境で内蔵 Auth を置き換えてはいない |

これはソースに基づく設計上の取捨選択で、過去の審査会議を作り話にしたものではありません。既に Spring Boot を使い、独自の利用者・テナント・権限を必要とするため、同じ安全基盤へプロトコルを組み込むと重複を減らせます。業務上の身元と権限はプロジェクト側の責任です。

Spring Security はフィルターチェーンと認証機構を提供します。認可サーバーモジュールはそれを OAuth2/OIDC に使います。役割テーブルそのものでも、「李さんがこの申請を承認できるか」を決める仕組みでもありません。

## 原文一：リクエストを整理して処理器へ渡す

ブラウザーから届くのはパラメーターです。フレームワークは内部オブジェクトへ変換し、認証マネージャーに渡します。認可エンドポイントのフィルターにある連続原文です。

原ファイル：OAuth2AuthorizationEndpointFilter.java、193–201 行、Spring Security 7.0.5。

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-request-dispatch.java.txt" -->
```java
			Authentication authentication = (Authentication) request
				.getAttribute(OAuth2AuthorizationCodeRequestAuthenticationToken.class.getName());
			if (authentication == null) {
				authentication = this.authenticationConverter.convert(request);
				if (authentication instanceof AbstractAuthenticationToken authenticationToken) {
					authenticationToken.setDetails(this.authenticationDetailsSource.buildDetails(request));
				}
			}
			Authentication authenticationResult = this.authenticationManager.authenticate(authentication);
```

最初は、前段の検査がリクエストオブジェクトを既に用意したか調べます。なければ converter が現在のリクエストを解析します。次に詳細情報を付け、authenticationManager へ処理を渡します。

Authentication はフレームワーク内部の共通型です。この変数名だけで利用者のパスワード確認が済んだとはいえません。解析と認証は別段階で、対応する処理器がクライアント、パラメーター、現在の利用者などを検査します。

プロジェクトでは専用の安全チェーンが認可サーバーの入口を選び、その後これらの処理器へ進みます。通常のパスワードログインはプロジェクトのコントローラーで扱い、別ページで説明します。

## 原文二：PKCE の原本とダイジェストを対応させる

公開クライアントは長期の秘密をブラウザーコードに安全に隠せません。コード交換では、フレームワークの公開クライアント処理器が codeVerifierAuthenticator.authenticateRequired を呼び、次のメソッドで材料を比較します。

原ファイル：CodeVerifierAuthenticator.java、151–170 行、Spring Security 7.0.5。

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/pkce-s256.java.txt" -->
```java
	private boolean codeVerifierValid(String codeVerifier, String codeChallenge, String codeChallengeMethod) {
		if (!StringUtils.hasText(codeVerifier)) {
			return false;
		}
		else if ("S256".equals(codeChallengeMethod)) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-256");
				byte[] digest = md.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
				String encodedVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
				return encodedVerifier.equals(codeChallenge);
			}
			catch (NoSuchAlgorithmException ex) {
				// It is unlikely that SHA-256 is not available on the server. If it is
				// not available,
				// there will likely be bigger issues as well. We default to SERVER_ERROR.
				throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
			}
		}
		return false;
	}
```

原本がなければ失敗です。S256 の分岐では SHA-256 を計算し、パディングなしの base64url に変換し、保存済みのダイジェストと比較します。一致すれば成功です。

このメソッドで認識しない方式は失敗になります。必要なアルゴリズムが使えない場合も、例外分岐でサーバーエラーを返し、通過させません。

既定のブラウザークライアントは PKCE を必須にしています。フロントはランダムな原本を作り、Web Crypto で同じダイジェストを計算し、交換まで原本を保持します。両側の計算が一致する必要があり、パラメーター名の変更で比較を代用することはできません。

PKCE によってブラウザーが長期秘密を守れるサーバーになるわけではありません。画面が悪意あるスクリプトに制御された場合には別の対策が必要で、コールバック制限なども省略できません。

## 原文三：正常な交換の後にコードを無効化する

トークン生成後、コードが使われたことも保存します。引用するのは正常経路で、誤ったクライアントを拒否する分岐ではありません。

原ファイル：OAuth2AuthorizationCodeAuthenticationProvider.java、294–299 行、Spring Security 7.0.5。

<!-- omni:source path="docs/reference/upstream/spring-security-7.0.5/authorization-code-consumption.java.txt" -->
```java
		// Invalidate the authorization code as it can only be used once
		authorizationBuilder.invalidate(authorizationCode.getToken());

		authorization = authorizationBuilder.build();

		this.authorizationService.save(authorization);
```

最初にコードを無効化し、更新した認可記録を組み立てて保存します。認可コードは交換を繰り返せる長期パスワードではありません。フレームワークはコードの有効性や再使用も検査します。失敗したら古い交換を連打せず、アプリの入口からやり直します。

アクセストークンの期限とコードの消費は別の話です。この原文でプロジェクト内の全 JWT が同時に取り消されるわけではありません。トークン、ログアウト、業務権限は最後のページで扱います。

## フレームワークとプロジェクトの責任分担

| 責任 | 担当 | 続けて読む場所 |
|---|---|---|
| OAuth2 の解析、交換材料の検証、コード処理 | Spring 認可サーバーの処理器 | 本ページの三つの原文 |
| ローカルアカウントと画像認証 | Auth コントローラーとサービス | [通常ログイン](authentication-password.jp.md) |
| セッション、PKCE 材料の保存、コールバック | プロジェクトの安全設定とフロント | [企業 SSO](authentication-oauth2.jp.md) |
| 機器・外部アカウント・登録・ポータルの接続 | プロジェクトの入口とアダプター | [デバイスとソーシャル](authentication-device-social.jp.md) |
| メニュー、API 権限、データ範囲、退出後の状態 | ゲートウェイ・業務サービス・フロントの規則 | [権限と問題の切り分け](authentication-security.jp.md) |

フレームワークはプロトコル維持の負担を減らしますが、クライアント登録、セッション連携、鍵保管には正しい設定が必要です。ソースに入口があっても、外部実アカウントでの接続成功は証明できません。

## アドレスが必要になったら責任から探す

分担を理解してから使う索引です。現在の既定プロトコル入口とプロジェクトの入口を示し、全入口の実測合格一覧ではありません。

| アドレス | 主な責任 |
|---|---|
| /api/auth/login | 通常のパスワードログイン。ローカルの結果を返す |
| /api/auth/session-login | 認可サーバー用の現在利用者の状態を作る |
| /oauth2/authorize | 認可要求とコードの発行 |
| /oauth2/token | 対応するグラントでトークンを交換 |
| /oauth2/jwks | 検証用の公開鍵を提供し、秘密鍵は出さない |
| /oauth2/device_authorization | 機器認可を始め、機器コードと短い利用者コードを取得 |
| /oauth2/device_verification | 利用者による短いコードの確認を処理 |

通常ログインはプロジェクトの結果ラッパーを使い、プロトコルは access_token、error などのフィールドを使います。同じ戻り値として扱わず、各契約に従って解析します。

## 原資料とライセンス

引用元は [7.0.5 ソースアーカイブ](https://repo.maven.apache.org/maven2/org/springframework/security/spring-security-oauth2-authorization-server/7.0.5/spring-security-oauth2-authorization-server-7.0.5-sources.jar)です。原文と Copyright 2004-present the original author or authors の表示を保ち、[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0) に従います。由来の記録にはアーカイブ、原ファイル、行範囲、断片のダイジェストを含めます。説明用の引用であり、実行時依存の追加ではありません。

Spring Security 7 への統合は [Spring の公式発表](https://spring.io/blog/2025/09/11/spring-authorization-server-moving-to-spring-security-7-0/)で確認できます。本文はプロジェクトの 7.0.5 を一貫して使用し、新しい Web 例の API へ置き換えていません。

完全な[ライセンス](../reference/upstream/spring-security-7.0.5/LICENSE.txt)と[出典記録](../reference/upstream/spring-security-7.0.5/provenance.json)は、このサイトに同梱しています。
