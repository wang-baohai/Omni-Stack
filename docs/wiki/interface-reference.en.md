# Interface reference and historical images

This page lists 68 historical layout image files retained from baseline main fc165e3; each article displays all four language directories. They do not prove this remediation's business execution or acceptance of the current UI. Legacy last_success_at entries bind neither image hashes nor container images, so they cannot establish actual capture times or environments. Prepare your own records using current guides; do not assume historical business IDs exist. A language directory does not guarantee complete translation of the pixels.

<!-- omni:modules:workflow -->
## workflow

| Page | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| Process Definitions (publish) | ![Process Definitions (Simplified Chinese)](../images/zh-CN/workflow-definitions.png) | ![Process Definitions (English)](../images/en-US/workflow-definitions.png) | ![Process Definitions (Japanese)](../images/ja-JP/workflow-definitions.png) | ![Process Definitions (Korean)](../images/ko-KR/workflow-definitions.png) |
| Process Instance Tracking (instance-tracking) | ![Process Instances (Simplified Chinese)](../images/zh-CN/workflow-instances.png) | ![Process Instances (English)](../images/en-US/workflow-instances.png) | ![Process Instances (Japanese)](../images/ja-JP/workflow-instances.png) | ![Process Instances (Korean)](../images/ko-KR/workflow-instances.png) |
| Statistics Dashboard (summary view) | ![Statistics Dashboard (Simplified Chinese)](../images/zh-CN/workflow-stats.png) | ![Statistics Dashboard (English)](../images/en-US/workflow-stats.png) | ![Statistics Dashboard (Japanese)](../images/ja-JP/workflow-stats.png) | ![Statistics Dashboard (Korean)](../images/ko-KR/workflow-stats.png) |
### Read-Only Detail Overlays (Four Languages)
| Overlay | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| Process Progress (instance-tracking) | ![Process Progress (Simplified Chinese)](../images/zh-CN/workflow-instance-progress.png) | ![Process Progress (English)](../images/en-US/workflow-instance-progress.png) | ![Process Progress (Japanese)](../images/ja-JP/workflow-instance-progress.png) | ![Process Progress (Korean)](../images/ko-KR/workflow-instance-progress.png) |
| Approval Records (approval) | ![Approval Records (Simplified Chinese)](../images/zh-CN/workflow-instance-approval-records.png) | ![Approval Records (English)](../images/en-US/workflow-instance-approval-records.png) | ![Approval Records (Japanese)](../images/ja-JP/workflow-instance-approval-records.png) | ![Approval Records (Korean)](../images/ko-KR/workflow-instance-approval-records.png) |
| Version History (publish) | ![Version History (Simplified Chinese)](../images/zh-CN/workflow-model-versions.png) | ![Version History (English)](../images/en-US/workflow-model-versions.png) | ![Version History (Japanese)](../images/ja-JP/workflow-model-versions.png) | ![Version History (Korean)](../images/ko-KR/workflow-model-versions.png) |

In the old version-history images, Chinese shows DRAFT version 0 while English/Japanese/Korean show PUBLISHED version 2. They are neither the same record across languages nor evidence of publication in this run. See the [Workflow guide](../guides/workflow-approval.en.md) for current design, publishing and approval steps.

<!-- omni:modules:end -->

<!-- omni:modules:platform -->
## outbox

| Page | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| Message Detail Overlay (message-status) | ![Message Detail (Simplified Chinese)](../images/zh-CN/monitor-mq-message-detail.png) | ![Message Detail (English)](../images/en-US/monitor-mq-message-detail.png) | ![Message Detail (Japanese)](../images/ja-JP/monitor-mq-message-detail.png) | ![Message Detail (Korean)](../images/ko-KR/monitor-mq-message-detail.png) |

The old Japanese and Korean MQ dialogs retain English titles and labels. Chinese shows a different PENDING message while the other locales show SENT; these images do not prove delivery of one message across four languages. The lower payload is cut off by the viewport, so inspect your own record for complete fields. See the [MQ fact document](../mq-reliability.en.md) for current lease, CAS, retry and consumer idempotency rules.

<!-- omni:modules:end -->

<!-- omni:modules:srm -->
## srm

### Supplier Quotation Flow Screenshots (four languages)
| Item | Content |
|---|---|
| Environment | The historical script defaults to frontend `127.0.0.1:3000`, reaching `omni-procurement` and `omni-srm`; this does not establish the old images' deployment. For new captures, run both applications and browser in local Docker and connect to the container frontend with `E2E_RUNTIME_MODE=external`. See the [capture configuration](../../omni-frontend/playwright.docs.config.ts) for parameters; do not run its default webServer on the host. |
| Data precondition | The historical setup script uses `admin` and official APIs to create a category/material → an approval rule bound to a `procurement-approval` model version → a requisition submitted and approved → an RFQ followed by `send` (DRAFT→SENT, inviting `supplier1`). Each locale scenario uses its own RFQ; this article did not rerun that write chain. |
| Operator | Historical setup uses `admin`; the page account is `supplier1` (the `SUPPLIER` role and an active `srm_supplier_portal_user` association). This is not a current least-privilege checklist for ordinary users. Current roles, data scope and candidate requirements are in the [SRM guide](../guides/srm-flow.en.md) and [Procurement guide](../guides/procurement-flow.en.md). |
| Token | `E2eTokenFixture` issues test JWTs with a 1200-second TTL from existing tenant users and database grants; this does not replace password login or real CAPTCHA. The caller must delete temporary files outside the repository; this article does not prove historical cleanup occurred. Keep tokens out of docs, logs and version control. |
| Mutation switch | The quotation suite skips when test tokens are missing or `E2E_MUTATIONS=true` is not explicitly set. The script's expectOk checks the switch after awaiting the network response; it is not an interceptor before sending a request. Execute only in an authorized isolated environment; this article does not run the suite. |
| Viewport | The old quotation images are 1440×900; the helper disables screenshot animations. The current helper fixes the clock only in `display-fixtures` mode, which cannot be assumed to have been the capture mode for every old image. |
#### Step 1: Invitation list (not quoted)
| zh-CN | en-US |
|---|---|
| ![Supplier portal RFQ invitation list (Simplified Chinese)](../images/zh-CN/srm-portal-quotation-invitations.png) | ![Supplier portal RFQ invitation list (English)](../images/en-US/srm-portal-quotation-invitations.png) |
| ja-JP | ko-KR |
|---|---|
| ![Supplier portal RFQ invitation list (Japanese)](../images/ja-JP/srm-portal-quotation-invitations.png) | ![Supplier portal RFQ invitation list (Korean)](../images/ko-KR/srm-portal-quotation-invitations.png) |
#### Step 2: Quotation form (unit price and validity)
| zh-CN | en-US |
|---|---|
| ![Supplier portal quotation form (Simplified Chinese)](../images/zh-CN/srm-portal-quotation-form.png) | ![Supplier portal quotation form (English)](../images/en-US/srm-portal-quotation-form.png) |
| ja-JP | ko-KR |
|---|---|
| ![Supplier portal quotation form (Japanese)](../images/ja-JP/srm-portal-quotation-form.png) | ![Supplier portal quotation form (Korean)](../images/ko-KR/srm-portal-quotation-form.png) |
#### Step 3: Submission success (QUOTED and total amount)
| zh-CN | en-US |
|---|---|
| ![Supplier portal quotation submitted (Simplified Chinese)](../images/zh-CN/srm-portal-quotation-submitted.png) | ![Supplier portal quotation submitted (English)](../images/en-US/srm-portal-quotation-submitted.png) |
| ja-JP | ko-KR |
|---|---|
| ![Supplier portal quotation submitted (Japanese)](../images/ja-JP/srm-portal-quotation-submitted.png) | ![Supplier portal quotation submitted (Korean)](../images/ko-KR/srm-portal-quotation-submitted.png) |
### SRM Administration Page Screenshots (four languages)
| Page | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| Suppliers (lifecycle) | ![Suppliers (Simplified Chinese)](../images/zh-CN/srm-suppliers.png) | ![Suppliers (English)](../images/en-US/srm-suppliers.png) | ![Suppliers (Japanese)](../images/ja-JP/srm-suppliers.png) | ![Suppliers (Korean)](../images/ko-KR/srm-suppliers.png) |
| Evaluations (evaluation) | ![Evaluations (Simplified Chinese)](../images/zh-CN/srm-evaluations.png) | ![Evaluations (English)](../images/en-US/srm-evaluations.png) | ![Evaluations (Japanese)](../images/ja-JP/srm-evaluations.png) | ![Evaluations (Korean)](../images/ko-KR/srm-evaluations.png) |
| Risk management (risk) | ![Risk management (Simplified Chinese)](../images/zh-CN/srm-risks.png) | ![Risk management (English)](../images/en-US/srm-risks.png) | ![Risk management (Japanese)](../images/ja-JP/srm-risks.png) | ![Risk management (Korean)](../images/ko-KR/srm-risks.png) |
| Risk indicator config (risk) | ![Risk indicator config (Simplified Chinese)](../images/zh-CN/srm-risk-config.png) | ![Risk indicator config (English)](../images/en-US/srm-risk-config.png) | ![Risk indicator config (Japanese)](../images/ja-JP/srm-risk-config.png) | ![Risk indicator config (Korean)](../images/ko-KR/srm-risk-config.png) |
| Invitations (invite) | ![Invitations (Simplified Chinese)](../images/zh-CN/srm-invites.png) | ![Invitations (English)](../images/en-US/srm-invites.png) | ![Invitations (Japanese)](../images/ja-JP/srm-invites.png) | ![Invitations (Korean)](../images/ko-KR/srm-invites.png) |
### Supplier Portal Responsive Stability Screenshots (four languages)
| Viewport | zh-CN | en-US | ja-JP | ko-KR |
|---|---|---|---|---|
| 390×844 mobile | ![Portal quotation list, mobile (Simplified Chinese)](../images/zh-CN/srm-portal-quotation-mobile.png) | ![Portal quotation list, mobile (English)](../images/en-US/srm-portal-quotation-mobile.png) | ![Portal quotation list, mobile (Japanese)](../images/ja-JP/srm-portal-quotation-mobile.png) | ![Portal quotation list, mobile (Korean)](../images/ko-KR/srm-portal-quotation-mobile.png) |
| 1024×768 tablet | ![Portal quotation list, tablet (Simplified Chinese)](../images/zh-CN/srm-portal-quotation-tablet.png) | ![Portal quotation list, tablet (English)](../images/en-US/srm-portal-quotation-tablet.png) | ![Portal quotation list, tablet (Japanese)](../images/ja-JP/srm-portal-quotation-tablet.png) | ![Portal quotation list, tablet (Korean)](../images/ko-KR/srm-portal-quotation-tablet.png) |

The two narrow viewports illustrate the portal list and tab layout; they do not prove mobile form entry, submission or a complete quotation journey. Historical quotation forms retain background bleed-through, wrapped Japanese/Korean expiry labels and the Chinese unit “件”. The images are not repainted to hide these gaps.

<!-- omni:modules:end -->

The [current audit](../reference/evidence/interface-reference-wiki-review-2026-10-17.json) distinguishes hash checks on 68 baseline images from actual inspection of 12 originals. This document is accepted through real AI_REVIEW; human document review is optional. Historical UI translation gaps, current business execution and final site acceptance retain their separate limits.
