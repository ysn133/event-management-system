# Budget service database

The Budget service deliberately uses a small schema focused on the features
shown in the application. It owns only budget-related data and contacts the
Event service over HTTP when it needs to validate an event.

## Tables owned by the service

### `budgets`

One budget per event.

| Column | Purpose |
|---|---|
| `id` | Budget identifier |
| `evenement_id` | Event identifier validated through the Event service |
| `montant_total` | Allocated amount |
| `depassement_autorise` | Allows expenses beyond the allocated amount |
| `statut` | `OUVERT` or `CLOTURE` |
| `created_at`, `updated_at` | Technical timestamps |

### `depenses`

Stores actual and planned expenses.

| Column | Purpose |
|---|---|
| `id` | Expense identifier |
| `budget_id` | Owning budget |
| `description` | Expense label |
| `montant` | Amount |
| `categorie` | Free-text category used for grouping and statistics |
| `reservation_salle_id` | Optional external Salle reservation identifier; unique when present |
| `date_depense` | Expense date |
| `statut` | `PREVUE`, `EN_ATTENTE`, `PAYEE`, or `ANNULEE` |
| `commentaire` | Optional internal note |
| `created_at`, `updated_at` | Technical timestamps |

## Intentionally excluded

The service does not store attachments, tags, persistent alerts, or audit logs.
This avoids file-storage management and tables that are not essential to the
project. Alert cards are calculated dynamically from the threshold and current
expense totals, so they always reflect the latest data.

## Main API

```text
POST   /api/budgets
GET    /api/budgets/{id}
GET    /api/budgets/evenement/{eventId}
PUT    /api/budgets/{id}
POST   /api/budgets/{id}/cloturer
POST   /api/budgets/{id}/rouvrir
GET    /api/budgets/{id}/bilan
GET    /api/budgets/{id}/statistiques

POST   /api/budgets/{budgetId}/depenses
GET    /api/budgets/{budgetId}/depenses
GET    /api/depenses/{id}
PUT    /api/depenses/{id}
PATCH  /api/depenses/{id}/statut
DELETE /api/depenses/{id}
```

Listing a budget's expenses automatically synchronizes room reservations for
that event through the Salle service. A reservation creates one linked expense;
later price, date, or cancellation changes update that expense. The database's
unique reservation identifier prevents duplicate expenses.

Schema changes are applied by `database/migrations/002_budget_frontend_support.sql`.
Migration `003_simplify_budget_schema.sql` removes attachment, tag, audit, and
persistent-alert tables. Migration `004_room_reservation_expense_source.sql`
adds the room reservation reference without a foreign key because the Salle
service owns reservation records. Migration `005_simplify_budget_fields.sql`
removes unused accounting fields and the planned category table.
