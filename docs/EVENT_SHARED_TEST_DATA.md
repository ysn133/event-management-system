# Shared Event Test Data

The following stable records are available in the shared Supabase database for
cross-service integration testing.

| Resource | ID | Description |
|---|---:|---|
| Organizer | `1` | EventFlow Team 20 Shared Organizer |
| Event | `1` | EventFlow Team 20 Shared Test Event |

Event details:

```json
{
  "id": 1,
  "organisateurId": 1,
  "titre": "EventFlow Team 20 Shared Test Event",
  "description": "Stable shared event for Team 20 microservice integration tests. Do not delete.",
  "dateDebut": "2026-12-15T09:00:00Z",
  "dateFin": "2026-12-15T18:00:00Z",
  "lieu": "Casablanca",
  "statut": "PLANIFIE"
}
```

When the Event service is running locally on its default port, retrieve it with:

```text
GET http://localhost:5001/api/evenements/1
```

Other services can use `evenementId: 1` when they need an existing event for
integration tests. Do not delete or repurpose these shared records.
