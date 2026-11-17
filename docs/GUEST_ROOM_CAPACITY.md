# Guest and room capacity rules

- Only non-cancelled, budget-approved reservations provide seats. A rejected edit
  does not remove the previous approved booking; a never-approved proposal provides none.
- Count each room once per event. Occupied seats are pending + accepted invitations;
  refused invitations release their seat.
- Cancelling a reservation must leave enough seats in the other approved rooms.
  Cancelling the last room is refused while any occupied seats remain.
- Reducing a room's capacity checks every event with an active approved booking.
  If any event would have too few seats, the entire room update is refused.
- Guests can still be registered while an event has no room (planning mode). The
  first approved booking must accommodate all those guests. Adding seats remains
  allowed even if older data is already over capacity.
- Failure to verify the guest count refuses the capacity-reducing change. Add a
  replacement room first, or reduce registrations, then retry. Paid costs still
  follow the existing Budget payment-history rules.

## Service ownership

Salles queries only its own room/reservation tables. It forwards the user's JWT to
`GET {INVITES_SERVICE_URL}/api/v1/evenements/{eventId}/occupation` and receives
`{ "evenement_id": 1, "places_occupees": 80 }`. This count-only endpoint never
calls Salles, avoiding a circular capacity request. Set `INVITES_SERVICE_URL`
(default `http://localhost:5003`); the local launcher sets it automatically.
The launcher binds Salles to `0.0.0.0:5242` so Dockerized Invités can reach it
through `host.docker.internal`. Binding Salles only to localhost breaks that call.

## Simultaneous requests

The services currently share PostgreSQL. They coordinate capacity changes and
guest-count increases with the existing transaction-scoped advisory lock:
`(0x494E5649 << 32) | (eventId & 0xFFFFFFFF)`.

This is infrastructure coordination, not access to another service's tables.
Salles holds its room row lock and event lock while verifying the guest count
over HTTP and committing the room change. Invités fetches capacity **after**
acquiring the same event lock; responses are refreshed under the lock too.
Multi-event capacity reductions acquire event keys in a consistent order.
Commit/rollback releases locks automatically; no extra business tables are needed.
If databases are separated later, this mechanism must be replaced with a
capacity-allocation protocol through web services—not silently removed.

## User-facing errors

`409` explains the occupied seats and the proposed remaining capacity. `503`
means the guest count could not be verified; no reduction is accepted. Rejected
reservation proposals retain their retry action, and an approved booking stays
active until cancellation succeeds.
