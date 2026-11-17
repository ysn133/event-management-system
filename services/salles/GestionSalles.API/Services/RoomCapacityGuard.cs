using GestionSalles.API.Data;
using GestionSalles.API.Integration;
using GestionSalles.API.Models;
using Microsoft.EntityFrameworkCore;

namespace GestionSalles.API.Services;

/// Uses only Salles data; occupancy is always retrieved through the Invités API.
public class RoomCapacityGuard(ApplicationDbContext db,IGuestOccupancyGateway guests)
{
    // Shared coordination key with Invités' InvitationRepository. No foreign table access.
    public static long EventLockKey(long eventId) => 0x494E564900000000L | (eventId & 0xFFFFFFFFL);

    public async Task LockEvents(IEnumerable<long> eventIds)
    {
        if (!db.Database.IsNpgsql()) return; // SQLite unit-test fixtures use a single connection.
        foreach (long key in eventIds.Select(EventLockKey).Distinct().Order())
            await db.Database.ExecuteSqlInterpolatedAsync($"SELECT pg_advisory_xact_lock({key})");
    }

    private async Task<List<ReservationSalle>> ApprovedReservations(long eventId) =>
        await db.ReservationsSalles.AsNoTracking().Include(r=>r.Salle)
            .Where(r=>r.EvenementId==eventId && r.BudgetApproved && r.Statut!="ANNULEE").ToListAsync();

    private static Dictionary<long,int> Rooms(IEnumerable<ReservationSalle> rows) =>
        rows.GroupBy(r=>r.SalleId).ToDictionary(g=>g.Key,g=>g.First().Salle.Capacite);

    private async Task Check(long eventId,long before,long after,string authorization)
    {
        // Increasing a known capacity must remain possible even for historical overbooking.
        if (before>0 && after>=before) return;
        long occupied=await guests.OccupiedSeats(eventId,authorization);
        if (occupied>after)
            throw new RemoteServiceFailure(409,$"Capacité insuffisante pour l’événement {eventId} : {occupied} invité(s) occupent une place, mais il ne resterait que {after} place(s). Ajoutez une salle de remplacement ou réduisez les inscriptions avant cette modification.");
    }

    // Caller holds the room transaction/row lock. Keep the event lock until final commit.
    public async Task ReservationChange(ReservationSalle reservation,RoomChange proposal,string authorization)
    {
        await LockEvents(new[]{reservation.EvenementId});
        var rows=await ApprovedReservations(reservation.EvenementId);
        var before=Rooms(rows);
        var after=Rooms(rows.Where(r=>r.Id!=reservation.Id));
        if (proposal.Statut!="ANNULEE") after[reservation.SalleId]=reservation.Salle.Capacite;
        if (before.Count==0 && after.Count==0) return;
        await Check(reservation.EvenementId,before.Values.Sum(v=>(long)v),after.Values.Sum(v=>(long)v),authorization);
    }

    // Caller first locks the room, preventing a new affected reservation from slipping in.
    public async Task RoomReduction(long roomId,int newCapacity,string authorization)
    {
        var events=await db.ReservationsSalles.AsNoTracking()
            .Where(r=>r.SalleId==roomId && r.BudgetApproved && r.Statut!="ANNULEE")
            .Select(r=>r.EvenementId).Distinct().ToListAsync();
        await LockEvents(events);
        foreach(long eventId in events)
        {
            var before=Rooms(await ApprovedReservations(eventId));
            var after=new Dictionary<long,int>(before);
            if (!after.ContainsKey(roomId)) continue;
            after[roomId]=newCapacity;
            await Check(eventId,before.Values.Sum(v=>(long)v),after.Values.Sum(v=>(long)v),authorization);
        }
    }
}
