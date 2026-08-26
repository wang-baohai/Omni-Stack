# Omni-Stack 全機能監査 修正レポート

> 基準：[2026-08-14 監査](full-functional-audit-2026-08-14.jp.md)。修正・再検証日：2026-08-17。人工確認待ちの技術翻訳ドラフトです。

## 1. 総合結果

32件の確定問題をすべて修正・再検証しました。blocker 5/5、severe 12/12、medium 15/15。Maven reactor 18/18、498 test failure/error/skip なし、frontend build 成功、lint 0 error（当時197 warning）、Chromium E2E 18/18、`npm audit` 0 vulnerability、15 container と宣言済み health check が正常でした。

当時の評価は 90/100 の pre-production candidate です。実 OAuth、capacity/chaos、backup restore、本番 telemetry、独立 security test は別の production gate です。その後 WP-08 が197 warning を解消し、Compose/module baseline も更新されたため、最新 evidence が旧 count を上書きします。

## 2. Blocker の終了

- B-01：Jackson 2/3 と共通日時形式に対応する Feign decoder で quotation、MQ、Asset backfill を復旧。
- B-02：Auth User DTO/VO allowlist と password JSON ignore で hash 出力を防止。
- B-03：Jackson 3 request string sanitizer と Jackson 2 互換で JSON XSS を復旧。
- B-04：SRM が `SRM_SUPPLIER_ONBOARDING` published model を冪等解決・初期化。
- B-05：Asset seed/initializer/guard/doc を `ASSET_TRANSFER` / `ASSET_DISPOSAL` に統一し数値 model ID UI を削除。

## 3. Severe の終了

User write を allowlist DTO と tenant validation に変更し、Docker/CI で test を実行しました。Secret は `.env` 必須、内部 port は loopback/private、公開入口は Frontend/Gateway のみです。Nginx HTML/static security header と CSP、assertion E2E、Base/Gateway test、real dashboard、named MySQL volume、XSS fail-safe、menu state/retry、empty permission fail-closed、403/404 deep link を実装しました。

## 4. Medium の終了

32桁 Trace ID、Asset searchable selector、approval processing/retry、Gateway/Nginx header 分担、script/port/README 四言語同期、lazy chunk と750KB budget、CRM MySQL CI、public form 空欄、tenant password 必須、warning 整理、Workflow 409、logout/expiry redirect、error/loading/empty 分離、Asset validation、正しい empty text、390×844 Portal を確認しました。

## 5. 再検証で追加修正

primary unit のない social/portal user を Asset candidate から除外し、tenant mismatch は fail closed を維持しました。Procurement backfill/consumer は tenant + TENANT scope を設定して `finally` で解除し、replay は duplicate card 0でした。Nginx header/cache、frontend dependency、tenant admin password、JDK 25 container warning も修正しました。

## 6. 証拠と残存 production gate

終了証拠：Maven 18/18・498 test、real MySQL 4 test、frontend build/lint、Chromium 18/18、dependency 0、HTTP security、Asset backfill created=8/replay duplicate=8、named volume、15 container。総合 90/100。

残りは real OAuth、capacity/soak/fault、backup/cross-host restore/DB rollback、production metrics/log/alert/SLO、独立 SAST/DAST/penetration、demo identity/secret 交換です。lint warning は後続 WP-08 で終了済みです。
