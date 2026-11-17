# Expense history and reconciliation

Budget stores the financial record; Rooms and Providers own the reservation/contract.
These are separate facts: cancelling a source is not proof of a refund.

| Operation | Unpaid linked expense | Paid linked expense |
|---|---|---|
| Confirm/update source | Update the same commitment, subject to available funds | Keep the recorded payment; refuse changes to its amount |
| Cancel source | Set `ANNULEE`, release commitment | Keep `PAYEE` and its amount/date/description; flag source cancelled |
| Source disappears | Require explicit review; do not infer cancellation from an outage | Retain payment and require review |
| Record payment in Budget | `EN_ATTENTE` → `PAYEE` for an active source | Remains paid; no manual reversal through the status selector |

Cancelled approved room reservations cannot be hard-deleted. A definitively refused,
never-approved proposal can be cancelled locally and removed. A pending request with
an uncertain outcome must first be retried. Provider contracts already have no delete route.

## Historical records

The Budget detail page shows **Rapprochement des dépenses** when review is needed.
New approved assignments still create their expense automatically through Budget.
Older, unlinked sources no longer create expenses merely because a page was opened.

1. Fetch the complete reservation/contract lists over HTTP. If either service fails,
   stop reconciliation; unavailable does not mean deleted.
2. Choose an existing manual expense explicitly. It must belong to this budget,
   be unlinked, not cancelled, and have the same amount. A paid expense retains its
   payment details. No description/amount matching is performed automatically.
3. Alternatively, confirm that no expense exists before creating an unpaid commitment.
   Creation follows the normal budget/overspending rule. A cancelled source cannot
   invent a payment or a new commitment.
4. Review discrepancies or missing sources individually. Confirmed missing unpaid
   sources release their commitment; paid costs remain. Historical paid amount
   differences must be verified in the source service, not overwritten in Budget.

Every decision re-reads the source, checks the preview token and locks the budget.
Unique source indexes prevent duplicate linked expenses. Pending/blocked proposals
are included in source lists but are not imported as approved costs.

| Service | Endpoint | Purpose |
|---|---|---|
| Budget | `GET /api/budgets/{id}/rapprochement` | Preview issues and unlinked manual expenses; no writes |
| Budget | `POST /api/budgets/{id}/rapprochement` | Apply one explicit decision |
| Providers | `GET /api/contrats-prestataire/evenement/{id}` | Authenticated source snapshot, including pending proposals |
| Rooms | `GET /api/reservations/evenement/{id}` | Complete reservation snapshot |

POST body: `type` (`ROOM`/`PROVIDER`), `sourceId`, `token` from the preview,
`action` (`LINK`, `CREATE`, `SYNC`, `RESOLVE_MISSING`). `LINK` also needs `expenseId`;
`CREATE` requires `confirmNoExistingExpense: true`. All Budget routes require JWT.

Migration `009_source_payment_history.sql` adds four small metadata columns to
`depenses`; it does not add audit/attachment tables, delete existing costs, infer
old payments, or auto-link records. `scripts/run-local.sh` applies it on startup.
Already-lost historical payment information cannot be reconstructed without evidence.
Refunds and partial payments remain outside this application's current model.
