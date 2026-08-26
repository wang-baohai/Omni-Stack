# Complete Requisition, RFQ, Quotation, Order, and Receipt Flow

Procurement owns materials, requisition approval rules, requisitions, RFQs, quotations, purchase orders, and receipts. Approval runtime stays in `omni-workflow`; Procurement does not depend on Flowable.

## 1. End-to-End Flow

```text
Maintain Materials → Configure Approval Rules → Create and Approve Requisition → Create RFQ
→ Invite Suppliers → Receive and Compare Quotations → Create Order → Supplier Confirms → Receive Goods
```

Each stage has an independent state machine. Never skip prerequisites by changing database status.

## 2. Materials

Categories and materials are tenant-shared master data. A material defines code, name, category, unit, asset-management flag, and default asset quantity semantics. Check references before deletion.

Only accepted asset-managed receipt lines emit asset candidates. `assetQuantity` is an exact positive integer; monetary and quantity values follow explicit decimal precision contracts.

## 3. Requisition Approval Rules

The business-oriented UI asks users to:

1. Select a category or default scope.
2. Define an inclusive minimum and exclusive maximum amount.
3. Select a current published procurement approval flow.
4. Preview the actual approval path.
5. Test a category and amount against the server resolver.
6. Resolve coverage gaps or conflicts before enabling.

Deactivation and deletion show newly uncovered scope. The server owns matching logic; the frontend never duplicates it.

## 4. Requisitions

A draft contains requester, unit, purpose, and lines. Submission locks critical fields and starts Workflow using a persistent idempotent start request. A `START_FAILED` record can be retried without creating a second process. Approval, rejection, withdrawal, and cancellation follow domain rules.

## 5. RFQ and Quotations

Create an RFQ from approved demand, invite active suppliers, and send it. Portal quotations arrive through reliable messaging and are consumed idempotently by event ID. Comparison covers price, tax, delivery, and notes. Confirm currency, tax basis, deadline, and supplier eligibility before award.

## 6. Purchase Orders

An awarded quotation produces a draft order, then sent, supplier-confirmed, partially received, fully received, or cancelled states. After sending, supplier and commercial terms are protected. Cancellation checks existing receipts.

## 7. Goods Receipt

Each line records actual quantity, quality, and asset quantity. Asset candidates require PASS quality, asset-managed material, and an exact positive quantity. Submission, message redelivery, and Asset backfill remain idempotent. Verify both procurement status and resulting asset cards.

## 8. Permissions and Diagnostics

Procurement administrators manage master data and rules; requesters manage visible requisitions; buyers own RFQ/orders/receipts; suppliers see only their Portal invitations and quotations. Correlate document number, Workflow instance, Outbox message, Inbox event, and Trace ID.

See the [Procurement Design](../design/procurement-design.md).

