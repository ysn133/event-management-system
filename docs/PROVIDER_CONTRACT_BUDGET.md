# Provider assignments and budget commitments

In an event, open **Prestataires → Ajouter un prestataire**. Select a provider,
describe the work, set the agreed price and service date, and validate.
The preview uses the latest available balance; Budget makes the final decision.

## Service responsibilities

| Service | Owns | Calls |
|---|---|---|
| Prestataires | Provider directory, contracts, pending contract changes | Auth to validate the caller, Event to validate an event, Budget to reserve/release funds |
| Budget | Allocated budgets and linked expenses | Salles to refresh existing room commitments before checking the remaining funds |
| Auth | User accounts and JWTs | — |

New contract operations access only Prestataires-owned database tables. They
never read or write the budget tables. Budget does not query contract tables.

## Rules

- A budget must exist and be open.
- A positive contract amount is committed immediately, including contracts in
  `EN_ATTENTE`. `CONFIRME` and `TERMINE` also remain committed. Completion does
  not prove payment, so the expense is not automatically marked `PAYEE`.
- With overspending disabled, the projected committed total must fit the budget.
- With overspending enabled, the operation succeeds and the UI shows the deficit.
- `ANNULE` cancels the linked expense and releases the committed amount.
- Linked expenses are managed from the event's contract form, not the expense editor.
- Editing a contract replaces its existing commitment; it does not add a second charge.

## HTTP API

| Method | Route | Purpose |
|---|---|---|
| POST | `/api/contrats-prestataire` on port 5002 | Create an assignment with a UUID `requestId` |
| PUT | `/api/contrats-prestataire/{id}` on port 5002 | Change description, amount, date or status, with the last known `version` |
| POST | `/api/contrats-prestataire/{id}/synchroniser` on port 5002 | Retry a stored proposal after an interrupted or refused Budget call |
| PUT | `/api/budgets/evenement/{eventId}/contrats-prestataire/{contractId}` on port 5004 | Reserve, update or cancel the linked expense; called by Prestataires |

All contract writes require the user's `Authorization: Bearer <JWT>`. The Budget
integration route additionally requires `X-Service-Key`, supplied by Prestataires.
The browser never receives the service key. Health checks and existing public
directory endpoints keep their previous behavior.

Example assignment body:

```json
{
  "requestId": "d45acece-0e7f-4d34-aef1-07a067fb13c2",
  "prestataireId": 1001,
  "evenementId": 1001,
  "prestation": "Buffet et pauses café",
  "montant": 2500,
  "datePrestation": "2027-01-10T12:00:00.000Z",
  "statut": "CONFIRME"
}
```

An update contains `prestation`, `montant`, `datePrestation`, `statut` and
`version` (the contract's `budgetSyncVersion`). Provider and event cannot be
changed on an existing contract. Assign another contract for that purpose.

## Interrupted requests and concurrent spending

Prestataires first stores a pending proposal, then calls Budget and records the
accepted contract. `budgetSyncState` is `PENDING`, `BLOCKED` after an explicit
Budget refusal, or `SYNCED` after successful completion. Pending proposals are
visible in the event but excluded from the count of validated contracts.

If a call times out, Budget may already have saved the expense. The pending
proposal is kept and the UI offers **Réessayer**. Budget identifies an expense
by contract id and version: replaying it does not charge twice, and stale versions
cannot overwrite later changes. A blocked proposal can be edited and submitted
with a new version. Existing accepted contract data stays intact until Budget
accepts its replacement. Creation requests also use a unique UUID to survive retries.

Budget locks its own budget row while checking and saving an expense. Manual
expenses and room synchronization use the same lock. Expense revisions detect
conflicting updates. No distributed SQL transaction spans the two services;
there can be a temporary pending state until a retry completes.

## Setup

Migration `007_provider_contract_budget.sql` adds pending proposal metadata to
`contrats_prestataire`, and an external contract id, contract version and technical
revision to `depenses`. The external id has no database foreign key and is unique.
Existing contracts remain visible. Historical contracts are not silently imported
as expenses: editing one will create its linked expense after budget approval.

`./scripts/run-local.sh` applies migrations and generates a persistent private
`.local/contract-service-key`, exporting it as `CONTRACT_SERVICE_KEY` to both
services. For manual startup, configure the same key on Budget and Prestataires.
Prestataires also accepts `AUTH_SERVICE_URL`, `EVENT_SERVICE_URL` and
`BUDGET_SERVICE_URL`; defaults are localhost ports 5005, 5001 and 5004.

Restart Budget and Prestataires after updating the code or their environment.

## Verification

Provider tests cover authentication, validation, idempotent creation, timeout
recovery, blocked proposals, updates, cancellation and stale versions. Budget
tests cover the spending rules, replay safety, protected source expenses and
concurrent commitments. Live PostgreSQL checks exercise the complete HTTP flow
and remove only their own temporary fixtures afterwards.
