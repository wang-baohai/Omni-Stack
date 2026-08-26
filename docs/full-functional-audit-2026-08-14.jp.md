# Omni-Stack 全機能監査レポート

> 監査日：2026-08-14。本書は修正前の履歴基準です。記録した32項目は後に修正・再検証されました。[2026-08-17 修正レポート](full-functional-audit-remediation-2026-08-17.jp.md)を参照してください。
>
> 技術翻訳ドラフトであり人工確認待ちです。ID と証拠値は中国語原文を保持します。

## 1. 当時の総合結論

機能の形は広く実装されていましたが、2026-08-14 時点で交付可能ではありませんでした。blocker 5件、severe 12件、medium 15件を記録しました。test skip の package は可能でも、backend、lint、cross-service、security、browser の真の gate は green ではありませんでした。

主な blocker は共通 Feign/Jackson 日時 decode、Auth password hash 漏えい、Spring Boot 4/Jackson 3 の JSON XSS 未動作、SRM onboarding model 未公開、Asset Workflow category seed 不一致でした。

## 2. 範囲と合格基準

code、API、DB/seed、RBAC/DataScope、frontend route/action、Compose runtime/log、文書、実 browser flow を監査しました。再現可能な core flow、正しい tenant/permission、説明可能な失敗、使用可能な default、build/test/lint green、実行可能 regression、文書と code の一致を合格条件としました。

対象は Auth、Base、Gateway、Workflow、CRM、SRM、Procurement、Asset、common starter、frontend、database/Compose、documentation です。

## 3. Build と runtime 証拠

Frontend production build は成功しましたが lint は2 error と約200 warning。test skip Maven package は成功しましたが full backend gate は SRM で失敗し、Procurement contract test も失敗しました。Asset test は成功しました。したがって image build は release gate の証拠ではありませんでした。

実行確認は password/CAPTCHA、register、device code、menu/RBAC、user task ownership、Workflow approval、CRM、SRM Portal、Procurement approval/retry、RFQ、Goods Receipt→Asset、MQ、XSS、管理者42 route を対象としました。

## 4. Cross-module 結果

- Authentication、menu isolation、task ownership、Workflow assignee、Procurement requisition の approve/reject/cancel/retry は再現可能。
- SELF、DEPT_AND_BELOW、tenant mismatch rejection を確認。
- Supplier Portal は `SUPPLIER` role と active association の両方が必要。
- SRM 作成は onboarding model 未公開で失敗。
- 日時を含む SRM→Procurement quotation、Procurement→Asset backfill、Base MQ aggregation が共通 decoder で失敗。
- Redis に XSS 設定があっても危険文字列が保存。
- 管理画面は表示できるが無権限 deep link が blank、network failure が empty data として表示される場合がある。

## 5. 問題一覧

Blocker B-01–B-05：共通日時 decoder、password hash、JSON XSS、SRM default Workflow、Asset category。

Severe S-01–S-12：user mass assignment、赤い quality gate と skip test、安全でない secret/port、static security header、弱い E2E、Base/Gateway test 不足、fake dashboard、DB persistence 文書矛盾、XSS fail-open、menu loop、empty permission fail-open、無権限 blank page。

Medium M-01–M-15：Trace 不足、数字 ID UI、approval feedback、header 重複、port drift、bundle、CRM DB test skip、default credential、runtime warning、Workflow conflict code、logout/expiry、error/empty 混同、Asset validation、誤った empty 文言、mobile Portal。

## 6. 文書と修正方針

文書は設計理解には有用でしたが、目標設計を完了済みと表現する箇所がありました。target、current implementation、verified evidence を分離し、SRM Workflow、Asset category、README の closed-loop、port、task behavior を code と同期する必要がありました。

P0 は security/core flow、P1 は backend/MySQL/lint/assertion E2E、P2 は selector/feedback/error/responsive/bundle、P3 は production operations/backup/observability/security/documentation を担当しました。現在状態は本書ではなく修正レポートと `docs/evidence/scaffold-upgrade/` を参照してください。
