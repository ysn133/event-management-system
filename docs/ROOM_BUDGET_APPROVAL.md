# Room reservation budget approval

The latest Salles branch integrated before this change is `7930457`.

## Rules

Creating a reservation, changing its price/dates, or confirming it now calls
Budget over HTTP. Budget reserves the full agreed price, including reservations
in `EN_ATTENTE`.

| Budget state | Result |
|---|---|
| Missing | Refuse validation; create a budget first |
| Closed | Refuse new commitments and changes |
| Insufficient funds, overspending disabled | Refuse any increase in committed spending |
| Overspending enabled | Accept and show the deficit in the event UI |
| Enough remaining funds | Accept and create/update one linked expense |

An affordable price change replaces the previous amount. Reducing or cancelling
an existing commitment is allowed on an open budget even if historical spending
already exceeds its allocation. New spending still follows the overspending rule.

Managed cancellations go through Budget as well. Unpaid commitments are released;
paid costs are retained (cancellation does not imply refund). Historical records
are reviewed explicitly; see [expense history](EXPENSE_HISTORY.md).

## Ownership and security

Salles writes only its room/reservation tables. It validates users with Auth and
events with Event through HTTP; it no longer queries the event table on creation.
Budget alone checks its own allocation/expenses and writes the linked expense.

Reservation writes forward the user's JWT. The Budget integration endpoint also
requires a private `ROOM_BUDGET_SERVICE_KEY`, separate from the provider service
key. The frontend never receives either key.

## API changes

| Service | Method and route | Purpose |
|---|---|---|
| Salles | `POST /api/reservations` | Create a proposal and request approval; pass a stable UUID `requestId` for retries |
| Salles | `PUT /api/reservations/{id}` | Propose dates/price with the last known `version` |
| Salles | `PATCH /api/reservations/{id}/confirmer` | Confirm after Budget approval |
| Salles | `PATCH /api/reservations/{id}/annuler` | Cancel; release unpaid commitment, retain paid history |
| Salles | `POST /api/reservations/{id}/synchroniser-budget` | Retry a stored proposal without creating another reservation/expense |
| Budget | `PUT /api/budgets/evenement/{eventId}/reservations-salle/{reservationId}` | Atomically check funds and save the linked expense |

Reservation responses include `budgetVersion`, `budgetSyncState` (`SYNCED`,
`PENDING` or `BLOCKED`) and `budgetApproved`. A new refused proposal has
`budgetApproved=false`; it is not a validated booking and does not make the room
unavailable. A refused price change preserves the previously approved dates and
price. An error after staging includes `reservationId` and `pending=true`.

For compatibility, clients that omit `requestId` receive a generated UUID. Such
clients must retain the returned reservation id to retry; the frontend supplies
a stable request id itself. Supplying `version` on updates detects stale forms.

## Failure recovery and concurrent requests

Proposals are persisted before the HTTP call. If the Budget response is lost,
the UI displays a pending proposal and offers **Réessayer**. Budget uses the
reservation id and increasing version to reject stale updates and accept exact
replays without charging twice. The source reservation is finalized only after
approval. Pending changes must be resolved before another edit.

Salles locks its own room row during staging/approval, preventing overlapping
bookings from racing. A pending proposal temporarily blocks other changes for
that room until resolved. Budget locks its budget row using the same mechanism
as provider commitments and manual expenses, so those services cannot spend the
same remaining money concurrently.

Legacy reservations (`budgetVersion=0`) retain the existing pull synchronization.
Once a reservation is managed by approval, Budget's pull skips it, preserving
the accepted expense while a proposal is pending. Existing historical records
are not automatically reconciled or deleted by this migration.

## Startup

Migration `008_room_budget_approval.sql` adds reservation proposal metadata and
the linked expense version. `./scripts/run-local.sh` generates the ignored
`.local/room-budget-key`, exports it to Salles and Budget, and applies migrations.
Restart the backends after changing the code/environment.

For manual startup, configure matching `ROOM_BUDGET_SERVICE_KEY` values on Salles
and Budget, plus `AUTH_SERVICE_URL`, `EVENT_SERVICE_URL` and `BUDGET_SERVICE_URL`
on Salles. Defaults are localhost ports 5005, 5001 and 5004.

## Verification

Salles tests cover creation, replay, refused price changes, blocked proposals,
timeout recovery and cancellations. Budget tests cover spending rules, shared
provider/room balances, source protection, and legacy/managed synchronization.
The live PostgreSQL check exercises concurrent room/provider approvals and
removes only its temporary events, rooms, reservations, contracts and budgets.
