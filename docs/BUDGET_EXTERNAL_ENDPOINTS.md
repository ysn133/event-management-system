# External Endpoints Required by the Budget Service

| Service | Endpoint | Description | Requirement |
|---|---|---|---|
| Events | `GET /api/evenements/{evenementId}` | Verifies that an event exists before creating its budget. | Required |
| Rooms | `GET /api/reservations/evenement/{evenementId}` | Retrieves all room reservations for an event. The Budget service automatically creates or updates the matching expense records when expenses are loaded. | Used |
| Providers | `GET /api/prestataires/{id}/services` | Retrieves the services and tariffs offered by a provider. This endpoint is available in the current Providers contract. | Optional |
| Providers | `GET /api/contrats-prestataire?evenementId={evenementId}` | Retrieves an event's provider contracts to synchronize provider expenses. This endpoint is not available yet and must be confirmed by the Providers team. | Optional |

The first version of the Budget service uses manual expense entry. Therefore,
it depends only on the Events service endpoint.
