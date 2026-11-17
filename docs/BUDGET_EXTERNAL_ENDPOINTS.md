# External Endpoints Required by the Budget Service

| Service | Endpoint | Description | Requirement |
|---|---|---|---|
| Events | `GET /api/evenements/{evenementId}` | Verifies that an event exists before creating its budget. | Required |
| Rooms | `GET /api/reservations/evenement/{evenementId}` | Retrieves reservations for reconciliation and refreshes already-linked historical records without inventing new expenses. | Used |
| Providers | `GET /api/prestataires/{id}/services` | Retrieves the services and tariffs offered by a provider. This endpoint is available in the current Providers contract. | Optional |
| Providers | `GET /api/contrats-prestataire/evenement/{evenementId}` | Retrieves all event contracts for explicit reconciliation, forwarding the user's JWT. | Used |

Provider contract assignments call the Budget API to reserve funds; see
[provider assignment flow](PROVIDER_CONTRACT_BUDGET.md) for routes and retry rules.

Room assignments also call Budget before validation; see
[room approval flow](ROOM_BUDGET_APPROVAL.md). The Rooms GET endpoint refreshes
historical reservations; approval-managed reservations use the write endpoint.
The exact write contract is in [room-commitments.openapi.yaml](api-contracts/room-commitments.openapi.yaml).
