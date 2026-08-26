# Complete SRM Administration and Supplier Portal Flow

SRM covers invitations, Portal enrollment, admission, lifecycle, evaluation, risk, and supplier quotations. Internal users and Portal users have distinct roles and data boundaries.

## 1. Invitation and Enrollment

1. An administrator creates an invitation.
2. The supplier registers an authentication account at `/portal-register`.
3. In Portal, the supplier submits invitation token, unique client request ID, and company profile.
4. SRM creates the supplier and a Saga asks Auth for the `SUPPLIER` role.
5. Successful role assignment and association move the supplier to review.

Enrollment requires both `inviteToken` and `requestId`; retries reuse the request ID. Portal user IDs never populate internal owner columns.

## 2. Admission and Lifecycle

```text
REGISTERING → PENDING_REVIEW → APPROVED
                     ↘ REJECTED → PENDING_REVIEW
APPROVED ↔ SUSPENDED
APPROVED ↔ BLACKLISTED
APPROVED/SUSPENDED → ELIMINATED
```

Only approved suppliers are selectable by Procurement. Rejected suppliers may resubmit; suspended suppliers may resume; blacklist actions require dedicated permission. Elimination is final. SRM coordinators keep business and Workflow states aligned for submit, withdraw, cancel, and start retry.

## 3. Profile and Children

Profile data includes company details, contacts, qualifications, and bank accounts. Portal users access only their active supplier association. Internal child-resource scope inherits through the supplier root instead of nonexistent owner columns.

## 4. Evaluation

Administrators create periods and score items. Evaluations move from pending to in progress and completed; completed results are visible to authorized suppliers. Backend validation enforces ranges, weights, and required items.

## 5. Risk

Indicator types and criteria produce GREEN, YELLOW, or RED results. A rule change requires recalculation or an explicit historical rule version; old results must not silently change meaning.

## 6. RFQ Quotations

After Procurement sends an RFQ, an invited active supplier can view details and deadline and submit a quotation. Reliable messaging delivers the quotation to Procurement, where event-ID idempotency prevents duplicates. Suppliers never see competing quotations, and closed or cancelled RFQs reject submissions.

## 7. Saga Recovery

Auth role assignment and SRM enrollment cross service boundaries. A failure enters a diagnosable retry state rather than rolling back a completed remote transaction. Correlate request ID, supplier ID, message ID, and Trace ID.

See [SRM](../srm.en.md) and the [SRM Design](../design/srm-design.md).

