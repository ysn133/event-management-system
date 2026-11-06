# External Endpoints Required by the Budget Service

| Service | Endpoint | Description | Requirement |
|---|---|---|---|
| Events | `GET /api/evenements/{evenementId}` | Verifies that an event exists before creating its budget. | Required |
| Rooms | `GET /api/reservations/{id}` | Retrieves the price and status of a room reservation if it needs to become an expense. | Optional |
| Rooms | `GET /api/reservations?evenementId={evenementId}` | Retrieves an event's room reservations to synchronize room expenses. The Rooms team must add or confirm the `evenementId` filter. | Optional |
| Providers | `GET /api/prestataires/{id}/services` | Retrieves the services and tariffs offered by a provider. This endpoint is available in the current Providers contract. | Optional |
| Providers | `GET /api/contrats-prestataire?evenementId={evenementId}` | Retrieves an event's provider contracts to synchronize provider expenses. This endpoint is not available yet and must be confirmed by the Providers team. | Optional |

The first version of the Budget service uses manual expense entry. Therefore,
it depends only on the Events service endpoint.
