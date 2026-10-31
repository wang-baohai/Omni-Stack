# Asset guide: from stock-in to the end of an asset’s working life

When a new computer arrives, who records it, who receives it, and who confirms receipt? Which actions should you use when it moves to another person, needs repair, or is scrapped? This guide follows those everyday questions. Start by checking who currently uses the item, then distinguish approval from the physical handover.

The screenshots show real operations with dedicated local demonstration accounts. They were taken at different stages. Discarding and scrapping use two separate sample cards; one card is not shown in two terminal states at once. Names, account names, and department numbers are demonstration data. Use objects you are authorized to handle.

## 1. Open the module and check your role

Opening your workspace from the home page creates a new browser tab. Select Asset Management in the application catalog or the workspace’s application dialog to open a separate asset tab. Inside the module, the sidebar and top-right search focus on this module. To switch applications, return to your original workspace tab.

Different roles have different responsibilities:

| Role | What the person normally sees and does |
| --- | --- |
| Asset administrator | Maintains the tenant’s ledger, records and allocates assets, sends them for repair, and initiates and completes transfers or disposals |
| Asset manager | Manages assets within authorized departments; an approval can be handled only when the task is actually assigned to the account |
| Asset user | Views personal assets, accepts an allocation, and returns an item; does not administer everyone’s assets |

Being an asset manager does not grant access to every approval task. Receiving a task also does not grant full management access to the module. When a menu or button is missing, check both your identity and the asset’s state.

## 2. Use the overview to decide what to inspect

Open Asset Overview to see counts by status. Switch the distribution between status, category, department, and location. This helps you notice, for example, items still waiting for acceptance or equipment that has not returned from repair.

The counts in the screenshot were real at capture time. Refresh after a transaction, but do not use an overview count as proof that a particular item has been handed over. Confirm that item in the ledger.

![Asset Overview: inspect counts and distributions before opening individual records](../images/en-US/asset-overview.png)

## 3. Find an asset, inspect its source, and read its history

Open Asset Ledger and search by name or asset number. Add status, managing department, location, or other available filters when needed. Search applies your conditions; Reset clears them. Use pagination or change the page size to see the remaining rows.

If the table is wide, scroll horizontally to inspect the status and actions on the right. Some left-hand columns leave the viewport in these screenshots. The search value and the detail drawer still identify the item.

![Asset Ledger: search, filters, pagination, and row actions](../images/en-US/asset-ledger.png)

Open Details to inspect the name, original value, location, current user, and department, then read Change History. The manual sample initially has no current user and a value of 125.50 CNY. The person responsible for managing it is not necessarily the person using it.

![Manual asset details: basic data, current use, and change history](../images/en-US/asset-detail.png)

History retains both the start of an operation and any later restoration. Check the current status to decide what is possible now; read history in time order to understand how it got there. Scroll down for longer histories. A screenshot shows only the visible portion, and changing the interface language does not rewrite saved business descriptions.

## 4. How a purchase receipt creates asset cards

After a purchase receipt passes inspection and is confirmed, eligible physical items receive asset cards automatically. Inspection must pass, the material must be managed as an asset, and the asset quantity must be a positive whole number. Two devices of the same model can therefore have two cards and be allocated or repaired separately.

Open a card in the ledger and compare its purchase order, goods receipt, and source information with the actual item. This image reuses the source card already verified in the procurement guide. It is different from the manual sample used later.

![Procurement asset: the actual order, receipt source, and amount remain visible](../images/en-US/procurement-assets.png)

If a confirmed receipt has not produced a card you can find, check your filters first. Then inspect its quality result, asset-management setting, and quantity. Do not repeatedly create manual cards for the same physical items. Keep the receipt number for an administrator to investigate if the result remains unclear.

## 5. Record an item without a procurement source

Use Manual Stock-in when recording existing equipment or another item without a purchase-receipt source. Fill in the name, category, specification, brand and model, purchase date, value, currency, and location. Select the responsible asset manager and check the department filled from that account. Add the warranty expiry and expected life when available.

To associate a supplier, enter a name in the supplier search and select an authorized result. The adjacent supplier name is filled automatically to retain the selected name. Do not invent a name there. If the right result is missing, check the supplier data and your access.

Enter an amount such as 125.50 as the form requests; do not append a currency or explanation in the amount field. Choose a valid manager and check that the department matches. Do not copy demonstration identifiers.

![Manual Stock-in: asset data and value](../images/en-US/asset-create-dialog.png)

Scroll down to check remarks, management responsibility, and the buttons at the bottom. Save after checking the details. The system assigns an asset number and starts the card in In Stock. Cancelling the form discards the input without creating a card.

![Lower part of the stock-in form: check responsibility before saving or cancelling](../images/en-US/asset-create-footer.png)

Manual stock-in does not create a purchase order or receipt source. A manual card and a procurement-generated card have different origins; adding a remark does not turn one into the other.

## 6. Edit, delete, and handle conflicting changes

Choose Edit on an eligible In Stock asset to change its name, brand and model, remarks, or other available data. The edit form has no separate location field; editing details is not a physical move. Check the item before submitting. If another person has changed it, refresh and inspect the new state instead of repeatedly submitting the old form.

![Edit Asset: inspect the existing data before making a change](../images/en-US/asset-edit-dialog.png)

Scroll to inspect responsibility and the bottom buttons too. Changing the person who manages the card does not deliver the item to a new user. Use allocation or transfer for the physical change of user.

![Lower part of the edit form: responsibility and available actions](../images/en-US/asset-edit-footer.png)

Delete removes an unused manual In Stock record with no current use or active operation. Check the name or number in the confirmation, then refresh the list after confirming. Procurement cards and assets with business history cannot use deletion as a substitute for retirement. Use the appropriate disposal process for a physical item leaving service.

## 7. Allocation is not yet acceptance

Find an eligible In Stock item in the ledger and choose Allocate. Select the recipient, check the department, and add a remark if needed. The recipient must be an enabled account belonging to that department. Your access limits the available choices.

![Allocate: select a recipient and check the department filled from the account](../images/en-US/asset-allocate-dialog.png)

After allocation, the asset is Pending Acceptance and the recipient and department are recorded. The administrator has arranged a recipient, but that person has not yet confirmed physical receipt. This screenshot shows the same demonstration asset after a later allocation.

![After allocation: Pending Acceptance is different from In Use](../images/en-US/asset-allocated.png)

To give an item already in use to someone else, do not allocate it again. Return it before reallocating, or use the transfer process described below.

## 8. Accept an allocation or return it before acceptance

The recipient opens My Assets in Asset Ledger, finds the Pending Acceptance item, and chooses Accept Asset. Check the number and confirm only when the item, account, and actual receipt match. An administrator cannot accept it on someone else’s behalf.

![Accept Asset: check the item before confirming personal receipt](../images/en-US/asset-accept-confirm.png)

Refresh My Assets after success. The status becomes In Use. The account in this image is the sample’s actual current user; another account in the same department does not gain access to that personal asset just because the department matches.

![My Assets: the item is In Use after the recipient accepts it](../images/en-US/asset-my-in-use.png)

If an allocated item should be returned before acceptance, choose Return on the Pending Acceptance row. There is no need to accept it first just to return it. Check that the physical item is not staying with you.

![Return before acceptance: a separate supported return path](../images/en-US/asset-return-before-accept-confirm.png)

## 9. Return an item that is already in use

After handing back the physical item, the current user chooses Return on the In Use row in My Assets. Check the number and confirm. This action applies only to eligible assets currently assigned to the signed-in person.

![Return after use: hand back the item before confirming](../images/en-US/asset-return-confirm.png)

After a successful return, the asset is In Stock and no longer assigned to the person. It therefore disappears from My Assets. An empty result does not necessarily mean data was lost. Ask an authorized manager to search for the same number in the ledger.

![After return: the item no longer appears in this account’s personal list](../images/en-US/asset-my-returned.png)

The manager can inspect Details to see that current use has been cleared while allocation, acceptance, and return remain in history. Procurement source information is retained for procurement-generated cards too.

![Return history: the item is now in stock and its past actions remain traceable](../images/en-US/asset-return-history.png)

## 10. Repair keeps the original usage relationship

A manager chooses Send for Repair on an In Use item and checks the confirmation. The status becomes Under Repair. During repair, do not treat it as available stock or initiate another allocation, transfer, or disposal.

![Confirm repair: check the asset and its current state](../images/en-US/asset-maintenance-confirm.png)

![During repair: the ledger shows Under Repair](../images/en-US/asset-maintenance.png)

When repair is finished and the item has been handed back, choose Return from Repair and confirm. It returns to In Use with the original user and department. This means returning from repair to the previous use, not returning the item to stock.

![Return from Repair: restore the original use without selecting a new recipient](../images/en-US/asset-maintenance-complete-confirm.png)

## 11. Start a transfer and let the assigned approver review it

Open Asset Transfer and choose Start Transfer. Select an available asset within your access that is not occupied by another operation. Choose the target user, check the department, and enter the target location and reason. Eligible choices may include In Stock, Pending Acceptance, or In Use items; use the actual options offered by the form.

![Transfer form: asset, target user, department, location, and reason](../images/en-US/asset-transfer-form.png)

This filled form is a preview before submission. An application number appears only after an actual submission. Open the resulting Details and compare the previous and proposed users and locations to check that the correct asset was selected.

![Pending transfer: the original usage and the proposed change are shown separately](../images/en-US/asset-transfer-pending-detail.png)

The approver returns to the original workspace tab and opens the task actually received in pending approvals. Read the existing usage and reason before approving or rejecting, add a comment, and confirm. The image shows the assigned approver selecting rejection; merely opening this dialog does not submit a decision.

![Actual transfer task: inspect the proposal before approving or rejecting](../images/en-US/asset-transfer-approval.png)

While the application is being processed, the asset is Transferring. The proposed recipient has not yet replaced the current user. This prevents two operations from handling the same item at once. Do not create another application to bypass the wait.

## 12. Approval must be followed by the physical handover

After approval, a manager returns to the asset tab, locates the original transfer, checks its result, and completes the physical handover. Only after confirming the recipient and location should the manager choose Complete Handover and confirm.

![Complete Handover: the physical step that follows approval](../images/en-US/asset-transfer-complete-confirm.png)

After completion, the application is Completed and the asset returns to use. Its current user, department, and location follow the transfer’s target. Responsibility for managing an asset is different from physically receiving it.

![Completed transfer: the application and handover have both reached their result](../images/en-US/asset-transfer-completed.png)

If approval is rejected, the proposal does not take effect and the asset returns to its previous state. This example retains the original user, department, and location of an In Use item. For an application started from another eligible state, check that original state instead.

![Rejected transfer: the original usage remains and history explains the restoration](../images/en-US/asset-transfer-rejected.png)

## 13. Distinguish discard, scrap, and final disposal confirmation

Open Asset Disposal and choose Start Disposal. Select an eligible item, choose Discard or Scrap, and enter the reason. Add the residual value and disposal method as appropriate. Check the physical item and selected type before submitting.

![Disposal form: inspect the item, choose the type, and enter the reason and residual value](../images/en-US/asset-disposal-form.png)

The asset becomes Pending Disposal. In the actual workspace task, the approver inspects the type, previous state, residual value, method, and reason, then approves or rejects it. Previous-state values and business descriptions shown in the task are retained data.

![Disposal approval: check the item, type, value, and reason](../images/en-US/asset-disposal-approval.png)

Approval alone has not completed the item’s departure from service. Confirm that the item has actually been collected, destroyed, or handed over as agreed. Then return to the original application and choose Complete Disposal.

![Final disposal confirmation: inspect the physical result after approval](../images/en-US/asset-disposal-complete-confirm.png)

The Discard sample becomes Discarded. It is a different physical sample from the Scrap card below. Neither terminal state can be restored through Return or another allocation.

![Discard completed: a dedicated sample card is Discarded](../images/en-US/asset-disposal-completed.png)

![Scrap completed: a separate sample card is Scrapped](../images/en-US/asset-disposal-scrapped.png)

If the approver rejects the request, no disposal takes effect. The asset returns to its previous state with the original usage preserved. A rejected proposal does not mean a physically disposed item has been brought back into service.

![Rejected disposal: no physical disposal occurred and the original use is restored](../images/en-US/asset-disposal-rejected.png)

## 14. Retry safely when the start result is uncertain

If the start result needs confirmation, search for the original application, open its details, and refresh. It may still be Pending Approval without a confirmed process instance. Do not create the same transaction again or assume approval has definitely failed.

An authorized administrator first fixes the missing prerequisite, such as the actual approver’s organizational scope. Once the prerequisite is restored, choose Retry on the original row, check its number, and confirm. Retrying uses the same application rather than copying it.

![Uncertain transfer start: preview Retry for the original application](../images/en-US/asset-transfer-retry-confirm.png)

![Uncertain disposal start: retain and retry the original application too](../images/en-US/asset-disposal-retry-confirm.png)

The confirmation dialog only shows the action about to be taken. Refresh Details and inspect the process instance and actual approver’s task to confirm a successful retry. After a decision arrives, check the asset through the approved or rejected path.

Local cancellation is available only when the page explicitly shows Start Failed and offers Cancel. Check the original application before using it. An uncertain result cannot use that cancellation path. Do not create a duplicate when Cancel is absent. These retry screenshots show an uncertain result, not an explicit Start Failed state.

## 15. Process cancellation and local application cancellation are different

When an already-started workflow is legitimately terminated, the transfer or disposal receives a cancellation result and the asset returns to its previous state. The images below show actual restoration and the latest history. The original user, department, and location are retained.

The current interface has no button for terminating a started process. These images show the result without inventing a sequence of clicks. They do not demonstrate the local cancellation of an explicitly failed start described above.

![After process cancellation of a transfer: the original usage is restored](../images/en-US/asset-transfer-cancelled.png)

![After process cancellation of a disposal: original use and the latest cancellation history](../images/en-US/asset-disposal-cancelled.png)

For either rejection or legitimate cancellation, inspect the original application and asset details to confirm the final result. An approved application still occupies the item until physical completion. Approval is not permission to start a second operation at the same time.

## 16. Check these things when data or buttons are missing

First check the browser tab and signed-in account, then filters and asset status, and finally the role and department scope. An empty list is not always a system failure.

This demonstration manager belongs to another department. The role exists, but the sample assets are outside its management scope, so no rows appear. Changing search parameters should not expose another department’s assets.

![Empty result outside the department scope: a role alone does not make these assets visible](../images/en-US/asset-scope-empty.png)

A personal asset user is restricted from management functions. Return to My Assets to handle personal items instead of turning the account into an administrator for everyday acceptance.

![Management access refused: the personal user lacks management permission](../images/en-US/asset-management-forbidden.png)

| Situation | Check first | What to do next |
| --- | --- | --- |
| A new allocation is missing from My Assets | Whether this is the intended recipient and whether old filters remain | Refresh, reset filters, and ask the manager to check the current user |
| An item disappears after Return | Whether it is now In Stock | Ask a manager to search its number and inspect status and history |
| Allocate, Edit, or Delete is absent | Identity, status, procurement source, and existing transactions | Use the action allowed in the current state instead of submitting repeatedly |
| No approval task arrives | Whether this account is actually assigned and approval prerequisites are complete | An authorized administrator checks candidates and organizational scope |
| Approval passed but the item is still Transferring or Pending Disposal | Whether physical handover or disposal was confirmed | Inspect the physical item, then complete the original application |
| The item is occupied by another application | Whether the original transfer or disposal has ended | Handle the original application before starting either type again |
| The result needs confirmation after submission | Whether the original application exists and has a process instance | Restore prerequisites and retry that application, without creating a new one |
| The record has been changed | Whether another person acted or the page is old | Refresh and inspect the current state before acting |
| The list or action refuses access | Whether this exceeds your role or department scope | Use your authorized entry; an authorized person handles management work |

For transfers and disposals requiring approval, check both the original application’s final status and the asset card’s current state and usage. That is how you confirm that approval, the physical item, and the recorded result agree.
